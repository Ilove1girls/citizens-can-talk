package me.sshcrack.mc_talking.deepseek;

public enum DeepSeekModel {
    CHAT("deepseek-chat"),
    REASONER("deepseek-reasoner");

    private final String modelName;

    DeepSeekModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    public static String getDefaultModel() {
        return CHAT.modelName;
    }
}
