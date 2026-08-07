# Commerce Migration

Commerce가 소유한 PostgreSQL Versioned Migration을 이 디렉터리에 둔다.

| Version | 내용 |
|---|---|
| `V1__create_catalog.sql` | `menu_category`, `product`, `outbox_event` (03 상품·메뉴 관리) |

실행된 Versioned Migration은 수정·삭제·이름 변경하지 않고 새 Forward-fix Migration을 추가한다.
