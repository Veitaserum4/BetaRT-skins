interface McrtxSettingsCategoryUi {
    int UPDATE_NONE = 0;
    int UPDATE_REFRESH = 1;
    int UPDATE_REBUILD = 2;

    String getName();
    void addControls(McrtxQuickSettingsScreen screen);
    int handleButton(int buttonId, int direction);
    void refreshButtons(McrtxQuickSettingsScreen screen);
    void applySavedSettings();
}
