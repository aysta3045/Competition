package aysta3045;

import aysta3045.screen.CompetitionScreen;
import aysta3045.screen.CompetitionManagementScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class CompetitionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册比赛管理屏幕
        HandledScreens.register(
                Competition.COMPETITION_SCREEN_HANDLER,
                CompetitionScreen::new
        );

        // 注册比赛管理屏幕（第二个GUI）
        HandledScreens.register(
                Competition.COMPETITION_MANAGEMENT_SCREEN_HANDLER,
                CompetitionManagementScreen::new
        );

        Competition.LOGGER.info("Competition client initialized!");
    }
}