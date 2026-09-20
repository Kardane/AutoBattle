package com.example.minigame;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서버사이드 전용 모드 초기화 클래스
 * DedicatedServerModInitializer를 사용하여 서버에서만 로직이 실행됨
 */
public class FabricMinigameTemplate implements DedicatedServerModInitializer {

	// 모드 ID 상수
	public static final String MOD_ID = "fabricminigametemplate";

	// 로거 인스턴스
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeServer() {
		LOGGER.info("[{}] 서버사이드 모드 초기화 시작", MOD_ID);

		// 커맨드 등록
		registerCommands();

		// 이벤트 리스너 등록
		registerEvents();

		LOGGER.info("[{}] 서버사이드 모드 초기화 완료", MOD_ID);
	}

	/**
	 * 서버 커맨드 등록
	 * CommandRegistrationCallback 사용
	 */
	private void registerCommands() {
		// TODO: 커맨드 등록 구현
		// CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess,
		// environment) -> {
		// dispatcher.register(CommandManager.literal("example")
		// .requires(Permissions.require("fabricminigametemplate.command.example", 2))
		// .executes(ctx -> {
		// ctx.getSource().sendFeedback(() -> Text.literal("Hello!"), false);
		// return Command.SINGLE_SUCCESS;
		// })
		// );
		// });
	}

	/**
	 * 서버 이벤트 리스너 등록
	 */
	private void registerEvents() {
		// TODO: 이벤트 리스너 구현
		// ServerLifecycleEvents.SERVER_STARTED.register(server -> {
		// LOGGER.info("[{}] 서버 시작 완료", MOD_ID);
		// });
	}
}
