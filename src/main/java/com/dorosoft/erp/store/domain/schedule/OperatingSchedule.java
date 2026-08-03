package com.dorosoft.erp.store.domain.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 영업시간·휴무·서비스 구간을 묶은 불변 스케줄. */
public final class OperatingSchedule {

    private static final int SECONDS_PER_DAY = 86_400;
    private static final int SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY;

    private final Map<DayOfWeek, List<BusinessPeriod>> businessHours;
    private final Set<DayOfWeek> regularClosedDays;
    private final Set<TemporaryClosure> temporaryClosures;
    private final Set<ServiceWindow> serviceWindows;

    private OperatingSchedule(
            Map<DayOfWeek, List<BusinessPeriod>> businessHours,
            Set<DayOfWeek> regularClosedDays,
            Set<TemporaryClosure> temporaryClosures,
            Set<ServiceWindow> serviceWindows) {
        this.businessHours = businessHours;
        this.regularClosedDays = regularClosedDays;
        this.temporaryClosures = temporaryClosures;
        this.serviceWindows = serviceWindows;
    }

    public static OperatingSchedule of(
            Map<DayOfWeek, List<BusinessPeriod>> businessHours,
            Set<DayOfWeek> regularClosedDays,
            Set<TemporaryClosure> temporaryClosures,
            Set<ServiceWindow> serviceWindows) {
        Objects.requireNonNull(businessHours, "businessHours는 null일 수 없습니다");
        Objects.requireNonNull(regularClosedDays, "regularClosedDays는 null일 수 없습니다");
        Objects.requireNonNull(temporaryClosures, "temporaryClosures는 null일 수 없습니다");
        Objects.requireNonNull(serviceWindows, "serviceWindows는 null일 수 없습니다");

        Map<DayOfWeek, List<BusinessPeriod>> copiedHours = new EnumMap<>(DayOfWeek.class);
        for (Map.Entry<DayOfWeek, List<BusinessPeriod>> entry : businessHours.entrySet()) {
            DayOfWeek day = Objects.requireNonNull(entry.getKey(), "businessHours의 요일은 null일 수 없습니다");
            List<BusinessPeriod> periods =
                    Objects.requireNonNull(entry.getValue(), "businessHours[" + day + "]는 null일 수 없습니다");
            copiedHours.put(day, List.copyOf(periods));
        }

        Set<DayOfWeek> copiedClosedDays =
                regularClosedDays.isEmpty()
                        ? EnumSet.noneOf(DayOfWeek.class)
                        : EnumSet.copyOf(regularClosedDays);

        return new OperatingSchedule(
                Collections.unmodifiableMap(copiedHours),
                Collections.unmodifiableSet(copiedClosedDays),
                Set.copyOf(temporaryClosures),
                Set.copyOf(serviceWindows));
    }

    public static OperatingSchedule empty() {
        return of(Map.of(), Set.of(), Set.of(), Set.of());
    }

    public Map<DayOfWeek, List<BusinessPeriod>> businessHours() {
        return businessHours;
    }

    public Set<DayOfWeek> regularClosedDays() {
        return regularClosedDays;
    }

    public Set<TemporaryClosure> temporaryClosures() {
        return temporaryClosures;
    }

    public Set<ServiceWindow> serviceWindows() {
        return serviceWindows;
    }

    /**
     * 스케줄 불변식 검증.
     *
     * <ol>
     *   <li>영업 구간끼리 겹치지 않는다.
     *   <li>모든 서비스 구간은 영업시간 합집합에 완전히 포함된다.
     *   <li>정기 휴무 요일에는 영업 구간·서비스 구간이 없다.
     *   <li>임시 휴무 날짜는 중복되지 않는다.
     * </ol>
     */
    public void validate() {
        List<LabeledSegment> businessSegments = businessSegments();
        verifyNoBusinessOverlap(businessSegments);
        verifyServiceWindowsWithinBusinessHours(businessSegments);
        verifyRegularClosedDaysHaveNoPeriod();
        verifyNoDuplicateTemporaryClosureDates();
    }

    public boolean isBusinessOpen(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant는 null일 수 없습니다");
        Objects.requireNonNull(zone, "zone은 null일 수 없습니다");

        LocalDate today = instant.atZone(zone).toLocalDate();
        if (isClosedOn(today)) {
            return false;
        }
        for (BusinessPeriod period : periodsOf(today.getDayOfWeek())) {
            if (period.toInterval(today, zone).contains(instant)) {
                return true;
            }
        }

        // 전날 자정을 넘긴 구간도 오늘 시각을 포함할 수 있다. 단 전날이 휴무면 영업하지 않았다.
        LocalDate yesterday = today.minusDays(1);
        if (isClosedOn(yesterday)) {
            return false;
        }
        for (BusinessPeriod period : periodsOf(yesterday.getDayOfWeek())) {
            if (period.crossesMidnight() && period.toInterval(yesterday, zone).contains(instant)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClosedOn(LocalDate date) {
        if (regularClosedDays.contains(date.getDayOfWeek())) {
            return true;
        }
        for (TemporaryClosure closure : temporaryClosures) {
            if (closure.date().equals(date)) {
                return true;
            }
        }
        return false;
    }

    private List<BusinessPeriod> periodsOf(DayOfWeek day) {
        return businessHours.getOrDefault(day, List.of());
    }

    // --- 주간 정규화 ---------------------------------------------------------

    /** 주 단위(초)로 감싼 구간 조각. end는 제외. */
    private record Segment(int start, int end) {
        boolean overlaps(Segment other) {
            return start < other.end && other.start < end;
        }

        boolean containedIn(Segment other) {
            return other.start <= start && end <= other.end;
        }
    }

    /** groupId는 하나의 원본 구간에서 쪼개진 조각을 묶는 식별자다. */
    private record LabeledSegment(int groupId, DayOfWeek day, String label, Segment segment) {}

    private static int startOffset(DayOfWeek day, LocalTime start) {
        return (day.getValue() - 1) * SECONDS_PER_DAY + start.toSecondOfDay();
    }

    private static int lengthSeconds(LocalTime start, LocalTime end) {
        int startSec = start.toSecondOfDay();
        int endSec = end.toSecondOfDay();
        return end.isBefore(start) ? (SECONDS_PER_DAY - startSec) + endSec : endSec - startSec;
    }

    /** 주 경계를 넘는 구간은 두 조각으로 쪼갠다. */
    private static List<Segment> normalizeWeeklyIntervals(
            DayOfWeek day, LocalTime start, LocalTime end) {
        int offset = startOffset(day, start);
        int length = lengthSeconds(start, end);
        int rawEnd = offset + length;
        if (rawEnd <= SECONDS_PER_WEEK) {
            return List.of(new Segment(offset, rawEnd));
        }
        return List.of(
                new Segment(offset, SECONDS_PER_WEEK), new Segment(0, rawEnd - SECONDS_PER_WEEK));
    }

    private List<LabeledSegment> businessSegments() {
        List<LabeledSegment> segments = new ArrayList<>();
        int groupId = 0;
        for (Map.Entry<DayOfWeek, List<BusinessPeriod>> entry : businessHours.entrySet()) {
            DayOfWeek day = entry.getKey();
            for (BusinessPeriod period : entry.getValue()) {
                String label = day + " " + period.start() + "~" + period.end();
                for (Segment segment : normalizeWeeklyIntervals(day, period.start(), period.end())) {
                    segments.add(new LabeledSegment(groupId, day, label, segment));
                }
                groupId++;
            }
        }
        return segments;
    }

    private static void verifyNoBusinessOverlap(List<LabeledSegment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            for (int j = i + 1; j < segments.size(); j++) {
                LabeledSegment left = segments.get(i);
                LabeledSegment right = segments.get(j);
                if (left.groupId() == right.groupId()) {
                    continue; // 같은 원본 구간이 쪼개진 조각끼리는 비교하지 않는다
                }
                if (left.segment().overlaps(right.segment())) {
                    throw new OperatingScheduleViolationException(
                            OperatingScheduleViolationException.Reason.OVERLAPPING_BUSINESS_HOURS,
                            "businessHours." + left.day(),
                            "영업 구간이 서로 겹칩니다: " + left.label() + " / " + right.label());
                }
            }
        }
    }

    private void verifyServiceWindowsWithinBusinessHours(List<LabeledSegment> businessSegments) {
        if (serviceWindows.isEmpty()) {
            return;
        }
        List<Segment> union = mergeAdjacent(businessSegments.stream().map(LabeledSegment::segment).toList());
        for (ServiceWindow window : serviceWindows) {
            for (Segment part :
                    normalizeWeeklyIntervals(window.dayOfWeek(), window.start(), window.end())) {
                if (union.stream().noneMatch(part::containedIn)) {
                    throw new OperatingScheduleViolationException(
                            OperatingScheduleViolationException.Reason
                                    .SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS,
                            "serviceWindows." + window.serviceType() + "." + window.dayOfWeek(),
                            "서비스 구간이 영업시간을 벗어납니다: "
                                    + window.serviceType()
                                    + " "
                                    + window.dayOfWeek()
                                    + " "
                                    + window.start()
                                    + "~"
                                    + window.end());
                }
            }
        }
    }

    /** 겹치거나 맞닿은 조각을 하나로 병합한다. */
    private static List<Segment> mergeAdjacent(List<Segment> segments) {
        if (segments.isEmpty()) {
            return List.of();
        }
        List<Segment> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparingInt(Segment::start).thenComparingInt(Segment::end));

        List<Segment> merged = new ArrayList<>();
        int currentStart = sorted.getFirst().start();
        int currentEnd = sorted.getFirst().end();
        for (Segment segment : sorted.subList(1, sorted.size())) {
            if (segment.start() <= currentEnd) {
                currentEnd = Math.max(currentEnd, segment.end());
            } else {
                merged.add(new Segment(currentStart, currentEnd));
                currentStart = segment.start();
                currentEnd = segment.end();
            }
        }
        merged.add(new Segment(currentStart, currentEnd));
        return merged;
    }

    private void verifyRegularClosedDaysHaveNoPeriod() {
        if (regularClosedDays.isEmpty()) {
            return;
        }
        Set<DayOfWeek> violations = new LinkedHashSet<>();
        for (Map.Entry<DayOfWeek, List<BusinessPeriod>> entry : businessHours.entrySet()) {
            if (!entry.getValue().isEmpty() && regularClosedDays.contains(entry.getKey())) {
                violations.add(entry.getKey());
            }
        }
        if (!violations.isEmpty()) {
            throw new OperatingScheduleViolationException(
                    OperatingScheduleViolationException.Reason.CLOSED_DAY_HAS_BUSINESS_HOURS,
                    "businessHours." + violations.iterator().next(),
                    "정기 휴무 요일에 영업 구간이 존재합니다: " + violations);
        }
        for (ServiceWindow window : serviceWindows) {
            if (regularClosedDays.contains(window.dayOfWeek())) {
                throw new OperatingScheduleViolationException(
                        OperatingScheduleViolationException.Reason.CLOSED_DAY_HAS_BUSINESS_HOURS,
                        "serviceWindows." + window.serviceType() + "." + window.dayOfWeek(),
                        "정기 휴무 요일에 서비스 구간이 존재합니다: "
                                + window.serviceType()
                                + " "
                                + window.dayOfWeek());
            }
        }
    }

    private void verifyNoDuplicateTemporaryClosureDates() {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (TemporaryClosure closure : temporaryClosures) {
            if (!dates.add(closure.date())) {
                throw new OperatingScheduleViolationException(
                        OperatingScheduleViolationException.Reason.DUPLICATE_TEMPORARY_CLOSURE,
                        "temporaryClosures",
                        "임시 휴무 날짜가 중복됩니다: " + closure.date());
            }
        }
    }
}
