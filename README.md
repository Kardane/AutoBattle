# FabricMinigameTemplate

Minecraft 1.21.8 Fabric 서버사이드 전용 모드 프로젝트 템플릿
Polymer + Patbox 라이브러리 풀셋 탑재

## 프로젝트 스택

| 항목 | 버전 |
|---|---|
| Minecraft | 1.21.8 |
| Fabric Loom | 1.15.5 |
| Fabric Loader | 0.18.0 |
| Fabric API | 0.136.0+1.21.8 |
| Java | 21 |

---

## 포함된 라이브러리

### Polymer 계열 (서버사이드 커스텀 콘텐츠)

| 모듈 | 버전 | 용도 |
|---|---|---|
| polymer-core | 0.13.9+1.21.8 | 서버사이드 아이템/블록/엔티티 기반 |
| polymer-blocks | 0.13.13+1.21.8 | 커스텀 블록 (바닐라 클라 호환) |
| polymer-resource-pack | 0.13.13+1.21.8 | 서버에서 리소스팩 자동 생성/배포 |
| polymer-virtual-entity | 0.13.9+1.21.8 | 가상 엔티티 (Display Entity 기반) |
| polymer-autohost | 0.13.13+1.21.8 | 리소스팩 자동 호스팅 |

### Patbox 유틸리티

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| SGUI | 1.10.2+1.21.8 | 서버사이드 GUI (체스트/모루 등) |
| PlaceholderAPI | 2.7.2+1.21.8 | 문자열 플레이스홀더 파싱 |
| Server Translations API | 2.5.1+1.21.5 | 다국어 번역 지원 |
| Map Canvas API | 0.5.1+1.21.5 | 서버사이드 맵 드로잉 |
| Player Data API | 0.8.0+1.21.6 | 플레이어 커스텀 데이터 저장 |
| Predicate API | 0.6.0+1.21.2 | 플레이어별 조건 체크 |

### 외부 라이브러리

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| DisguiseLib | 1.3.12-1.21.8 (GitHub Release v1.21.8.7) | 서버사이드 엔티티 위장 API |
| GCB-server | 1.0.1+1.21.8 | GCB 서버 모듈 |
| danta-shader | 1.0.2+1.21.8 | 미니게임 셰이더 모듈 |
| fabric-permissions-api | 0.4.0 | 권한 체크 API |

DisguiseLib는 로컬 `vendor` jar를 사용하지 않고 GitHub Release asset을 Ivy 저장소로 받아옵니다.
GCB-server와 danta-shader는 `https://repo.biryeong.kim/releases/` Maven 저장소에서 받아옵니다.

---

## 빌드 & 실행

```powershell
# 소스 생성 (IDE용)
.\gradlew.bat genSources

# 빌드
.\gradlew.bat build

# 서버 실행 (개발용)
.\gradlew.bat runServer
```

---

## 프로젝트 구조

```
src/main/
├── java/com/example/minigame/
│   ├── FabricMinigameTemplate.java   # 메인 서버 초기화
│   └── mixin/
│       └── ExampleMixin.java         # Mixin 예제
└── resources/
    ├── fabric.mod.json               # 모드 메타데이터
    └── fabricminigametemplate.mixins.json  # Mixin 설정
```

---

## 라이브러리 사용법

### 1. Polymer Core — 서버사이드 아이템/블록

바닐라 클라이언트에서 커스텀 블록/아이템을 표시할 수 있게 해줌
서버에서만 로직이 돌아가고, 클라이언트에는 바닐라 아이템으로 위장해서 보내는 원리

```java
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

// 커스텀 아이템 정의
public class CustomItem extends Item implements PolymerItem {
    public CustomItem(Settings settings) {
        super(settings);
    }

    // 클라이언트에 어떤 바닐라 아이템으로 보일지 결정
    @Override
    public Item getPolymerItem(ItemStack itemStack, ServerPlayerEntity player) {
        return Items.DIAMOND_SWORD;
    }
}
```

> 📖 공식 문서: https://polymer.pb4.eu/

### 2. Polymer Blocks — 커스텀 블록

바닐라 블록 상태를 활용하여 커스텀 블록을 구현
클라이언트 모드 없이도 커스텀 블록이 바닐라처럼 동작

```java
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

// 커스텀 블록 (바닐라 텍스처 대체)
public class CustomBlock extends Block implements PolymerTexturedBlock {
    private final BlockState polymerBlockState;

    public CustomBlock(Settings settings) {
        super(settings);
        // 바닐라 블록 모델 요청 (리소스팩에서 자동 처리)
        this.polymerBlockState = PolymerBlockResourceUtils.requestBlock(
            PolymerBlockModel.of(/* 리소스 ID */)
        );
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return this.polymerBlockState;
    }
}
```

### 3. Polymer Virtual Entity — 가상 엔티티

실제 엔티티 없이 Display Entity 기반으로 가상 엔티티를 렌더링
서버사이드에서만 관리되며 클라이언트에는 패킷으로만 전달

```java
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

// 가상 텍스트 디스플레이 생성
ElementHolder holder = new ElementHolder();
TextDisplayElement textDisplay = new TextDisplayElement();
textDisplay.setText(Text.literal("Hello World!"));
holder.addElement(textDisplay);

// 플레이어에게 표시
// holder.addPlayer(player);
```

### 4. Polymer Autohost — 리소스팩 자동 호스팅

서버가 자동으로 리소스팩을 빌드하고 HTTP로 호스팅
별도 웹서버 없이 커스텀 텍스처/모델 배포 가능

```java
// polymer-autohost가 클래스패스에 있으면 자동으로 동작
// config/polymer/auto-host.json에서 포트/설정 변경 가능
// 별도 코드 없이도 polymer-resource-pack과 연동됨
```

### 5. SGUI — 서버사이드 GUI

체스트, 모루, 화로 등의 GUI를 서버사이드에서 제어
클라이언트 모드 없이 커스텀 GUI 구현 가능

```java
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

// 3줄짜리 체스트 GUI 생성
SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
gui.setTitle(Text.literal("미니게임 메뉴"));

// 슬롯에 아이템 배치
gui.setSlot(13, new GuiElementBuilder(Items.DIAMOND)
    .setName(Text.literal("게임 시작"))
    .setCallback((index, type, action) -> {
        // 클릭 시 실행할 로직
        player.sendMessage(Text.literal("게임을 시작합니다!"));
        gui.close();
    })
);

gui.open();
```

> 📖 GitHub: https://github.com/Patbox/sgui

### 6. Text Placeholder API — 텍스트 플레이스홀더

문자열 내 `%modid:type%` 형식의 플레이스홀더를 실시간으로 치환
설정 파일, 채팅, 스코어보드 등에서 동적 텍스트 표시

```java
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

// 커스텀 플레이스홀더 등록
Placeholders.register(
    Identifier.of("mymod", "player_score"),
    (ctx, argument) -> {
        if (ctx.hasPlayer()) {
            int score = getPlayerScore(ctx.player());
            return PlaceholderResult.value(Text.literal(String.valueOf(score)));
        }
        return PlaceholderResult.invalid("플레이어 필요");
    }
);

// 플레이스홀더가 포함된 텍스트 파싱
// 사용자 입력: "점수: %mymod:player_score%"
Text parsed = Placeholders.parseText(
    Text.literal("점수: %mymod:player_score%"),
    PlaceholderContext.of(player)
);
```

> 📖 공식 문서: https://placeholders.pb4.eu/

### 7. Map Canvas API — 서버사이드 맵 드로잉

지도 아이템에 서버사이드에서 직접 그림을 그릴 수 있음
런타임 상태만 사용하여 ID 충돌 없이 동작

```java
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.CanvasColor;
import eu.pb4.mapcanvas.api.utils.VirtualDisplay;

// 캔버스 생성 (128x128 = 기본 맵 크기)
DrawableCanvas canvas = DrawableCanvas.create();

// 픽셀 직접 그리기
canvas.set(64, 64, CanvasColor.RED_HIGH);

// 사각형 채우기
for (int x = 10; x < 50; x++) {
    for (int y = 10; y < 50; y++) {
        canvas.set(x, y, CanvasColor.BLUE_NORMAL);
    }
}

// VirtualDisplay로 플레이어에게 표시
// 사용 후 반드시 플레이어를 제거해서 불필요한 패킷 방지
```

> 📖 GitHub: https://github.com/Patbox/map-canvas-api

### 8. Player Data API — 플레이어 커스텀 데이터

엔티티 NBT와 별도로 플레이어별 추가 데이터를 저장하는 마이크로 라이브러리
서버 재시작 후에도 데이터 유지

```java
import eu.pb4.playerdata.api.PlayerDataApi;
import eu.pb4.playerdata.api.storage.NbtDataStorage;
import eu.pb4.playerdata.api.storage.JsonDataStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

// NbtDataStorage 등록 (모드 초기화 시)
NbtDataStorage storage = new NbtDataStorage("minigame_stats");
PlayerDataApi.register(storage);

// 데이터 쓰기
NbtCompound data = new NbtCompound();
data.putInt("wins", 10);
data.putInt("losses", 3);
PlayerDataApi.setCustomDataFor(player, storage, data);

// 데이터 읽기
NbtCompound loaded = PlayerDataApi.getCustomDataFor(player, storage);
int wins = loaded.getInt("wins");
```

> 📖 GitHub: https://github.com/Patbox/PlayerDataAPI

### 9. Predicate API — 플레이어 조건 체크

Codec 기반으로 플레이어별 조건을 정의하고 체크하는 API
설정 파일에서 JSON으로 조건을 정의하고 런타임에 평가

```java
import eu.pb4.predicate.api.MinecraftPredicateTypes;
import eu.pb4.predicate.api.PredicateContext;
import eu.pb4.predicate.api.BuiltinPredicates;
import net.minecraft.server.network.ServerPlayerEntity;

// 빌트인 조건 타입들:
// - permission: 권한 체크
// - operator_level: OP 레벨 체크
// - dimension: 차원 체크
// - in_block: 특정 블록 안에 있는지
// - has_advancement: 발전과제 보유 여부

// JSON 설정에서 조건을 정의하는 형식:
// {
//   "type": "operator_level",
//   "level": 2
// }

// 혹은 복합 조건:
// {
//   "type": "and",
//   "predicates": [
//     { "type": "permission", "permission": "mymod.vip" },
//     { "type": "dimension", "dimension": "minecraft:overworld" }
//   ]
// }
```

> 📖 GitHub: https://github.com/Patbox/PredicateAPI

### 10. Fabric Permissions API — 권한 관리

간단한 권한 체크 API. LuckPerms 등 권한 관리 모드와 연동됨
OP 레벨 기반 폴백 지원

```java
import me.lucko.fabric.api.permissions.v0.Permissions;
import me.lucko.fabric.api.permissions.v0.Options;

// 기본 권한 체크
if (Permissions.check(player, "mymod.permission")) {
    // 권한 있음
}

// OP 레벨 폴백 (권한 미설정 시 OP 4 이상이면 통과)
if (Permissions.check(player, "mymod.admin", 4)) {
    // 권한 있거나 OP 4 이상
}

// boolean 폴백 (권한 미설정 시 기본값 true)
if (Permissions.check(player, "mymod.feature", true)) {
    // 권한 있거나 미설정
}

// 커맨드 등록 시 권한 요구
CommandManager.literal("admin")
    .requires(Permissions.require("mymod.command.admin", 4))
    .executes(ctx -> {
        ctx.getSource().sendFeedback(() -> Text.literal("Admin!"), false);
        return Command.SINGLE_SUCCESS;
    });

// 옵션(메타데이터) 조회
Optional<String> prefix = Options.get(player, "prefix");
int balance = Options.get(player, "balance", 0, Integer::parseInt);
```

> 📖 GitHub: https://github.com/lucko/fabric-permissions-api

### 11. Server Translations API — 다국어 지원

서버에서 클라이언트 언어에 맞는 번역을 자동으로 전달
`lang/*.json` 파일을 만들면 자동으로 로드됨

```
src/main/resources/
└── assets/<mod_id>/
    └── lang/
        ├── en_us.json
        └── ko_kr.json
```

```json
// en_us.json
{
  "text.fabricminigametemplate.welcome": "Welcome to the minigame!",
  "text.fabricminigametemplate.score": "Score: %s"
}

// ko_kr.json
{
  "text.fabricminigametemplate.welcome": "미니게임에 오신 것을 환영합니다!",
  "text.fabricminigametemplate.score": "점수: %s"
}
```

```java
// 번역 가능한 텍스트 사용 (클라이언트 언어에 따라 자동 번역)
Text welcome = Text.translatable("text.fabricminigametemplate.welcome");
player.sendMessage(welcome);
```

> 📖 GitHub: https://github.com/NucleoidMC/Server-Translations

---

## 라이센스

MIT License
