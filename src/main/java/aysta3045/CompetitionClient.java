package aysta3045;

import aysta3045.screen.CompetitionScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class CompetitionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册屏幕
        HandledScreens.register(
                Competition.COMPETITION_SCREEN_HANDLER,
                CompetitionScreen::new
        );

        Competition.LOGGER.info("Competition client initialized!");
    }
}