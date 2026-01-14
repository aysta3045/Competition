package aysta3045;

import aysta3045.command.OpenGuiCommand;
import aysta3045.screen.CompetitionScreenHandler;
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

	// 注册屏幕处理器
	public static final ScreenHandlerType<CompetitionScreenHandler> COMPETITION_SCREEN_HANDLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(MOD_ID, "competition_gui"),
					new ScreenHandlerType<>(CompetitionScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
			);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");

		// 注册命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			OpenGuiCommand.register(dispatcher);
		});

		LOGGER.info("Competition GUI command registered!");
	}
}