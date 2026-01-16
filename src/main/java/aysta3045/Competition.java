package aysta3045;

import aysta3045.command.OpenGuiCommand;
import aysta3045.command.CloseCommandsCommand; // 添加导入
import aysta3045.command.StartPreparationCommand;
import aysta3045.screen.CompetitionScreenHandler;
import aysta3045.screen.CompetitionManagementScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Competition implements ModInitializer {
	public static final String MOD_ID = "competition";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 注册第一个GUI的屏幕处理器（原来的比赛GUI）
	public static final ScreenHandlerType<CompetitionScreenHandler> COMPETITION_SCREEN_HANDLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(MOD_ID, "competition_gui"),
					new ScreenHandlerType<>(CompetitionScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
			);

	// 注册第二个GUI的屏幕处理器（比赛管理GUI）
	public static final ScreenHandlerType<CompetitionManagementScreenHandler> COMPETITION_MANAGEMENT_SCREEN_HANDLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(MOD_ID, "competition_management_gui"),
					new ScreenHandlerType<>(CompetitionManagementScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
			);

	@Override
	public void onInitialize() {

		// 注册命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			OpenGuiCommand.register(dispatcher);
			CloseCommandsCommand.register(dispatcher); // 注册关闭命令权限的命令
			StartPreparationCommand.register(dispatcher);
		});

		LOGGER.info("Competition command registered!");
		LOGGER.info("Successfully registered Competition GUI");
	}
}