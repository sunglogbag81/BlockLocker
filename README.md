BlockLocker
===========

표지판으로 Minecraft의 개별 블록(상자 등)을 잠그는 플러그인입니다.

주요 기능
--------

* `[개인]` / `[Private]`, `[추가 사용자]` / `[More Users]` 표지판으로 상자, 화로, 문 및 여러 컨테이너를 보호합니다.
* 컨테이너에 표지판을 붙이면 `[개인]`과 플레이어 이름이 자동으로 입력됩니다.
* 보호 소유자만 보호를 파괴할 수 있습니다.
* 관리자는 보호된 문과 컨테이너를 열 수 있습니다.
* UUID 지원
  * UUID는 표지판의 숨겨진 데이터에 저장되며 일반 유저에게 보이지 않습니다.
  * Lockette/Deadbolt 표지판을 읽을 때 UUID를 자동 조회합니다.
* 설정 가능
  * 메시지는 한국어로 번역되어 있으며, `translations-kr.yml`을 수정해 커스터마이징할 수 있습니다.
  * 보호 가능한 블록 종류를 변경할 수 있습니다.
* 그룹 지원: 표지판에 `[MyGroup]`을 추가하면 `blocklocker.group.mygroup` 권한, 스코어보드 팀, MassiveCraft Factions의 같은 이름 팩션 유저가 접근할 수 있습니다.
* 복잡한 블록 처리
  * 양문 지원: 한쪽을 보호하면 반대쪽도 보호되고, 한쪽을 열면 반대쪽도 함께 열립니다.
  * 문 자동 닫힘 설정 지원
  * 큰 상자 지원: 한쪽을 보호하면 다른 한쪽도 보호됩니다.
  * 큰 상자의 컨테이너 설정은 두 블록 모두에 함께 저장됩니다.
  * 트랩도어/울타리 문도 본체 또는 지지 블록에 표지판을 붙일 수 있습니다.
* 보호 소유자는 `/blocklocker list`로 접근 목록을 확인하고 `/blocklocker trust <플레이어>` / `/blocklocker untrust <플레이어>`로 접근 플레이어를 관리하며 `/blocklocker transfer <플레이어>`로 소유권을 이전할 수 있습니다.
* `/blocklocker setting`으로 보호 컨테이너의 호퍼/공용/골렘 접근 설정 GUI를 열 수 있습니다.
* 업데이트 알림을 지원합니다.

빌드
----

Maven을 사용합니다.

```bash
mvn clean package
```

빌드 후 `target` 폴더에 `blocklocker-XX.jar` 파일이 생성됩니다.

명령어
------

```text
/blocklocker help
/blocklocker reload
/blocklocker setting
/blocklocker setting on
/blocklocker setting off
/blocklocker setting toggle <hopper-input|hopper-output|public-access|golem-access>
/blocklocker list
/blocklocker trust <플레이어>
/blocklocker untrust <플레이어>
/blocklocker transfer <플레이어>
/blocklocker bypass <add|remove|list> [플레이어]
```

권한
----

* `blocklocker.protect` — 컨테이너와 문 보호
* `blocklocker.bypass` — 다른 사람의 보호 우회
* `blocklocker.bypass.manage` — 우회 허용 플레이어 관리
* `blocklocker.admin` — 다른 사람의 보호 표지판 수정/제거
* `blocklocker.reload` — 플러그인 리로드
* `blocklocker.wilderness` — Towny 클레임 밖 야생 지역에 상자 설치

개발자 API
----------

다른 플러그인에서 BlockLocker와 연동하려면 `BlockLockerAPI` 또는 `BlockLockerAPIv2`를 사용할 수 있습니다.

플러그인 활성화 확인:

```java
boolean enabled = Bukkit.getPluginManager().getPlugin("BlockLocker") != null;
```

소유자 조회 예시:

```java
Optional<OfflinePlayer> owner = BlockLockerAPI.getOwner(block);
```

Redstone 접근 가능 여부 확인 예시:

```java
private boolean isRedstoneAllowed(Block block) {
    BlockLockerPlugin plugin = BlockLockerAPIv2.getPlugin();
    Optional<Protection> protection = plugin.getProtectionFinder().findProtection(block);
    if (!protection.isPresent()) {
        return true;
    }
    Profile redstoneProfile = plugin.getProfileFactory().fromRedstone();
    return protection.get().isAllowed(redstoneProfile);
}
```
