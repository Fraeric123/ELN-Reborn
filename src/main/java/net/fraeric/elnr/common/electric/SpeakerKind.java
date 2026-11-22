package net.fraeric.elnr.common.electric;

/** The sound blocks and the sounds each of them can play. */
public enum SpeakerKind {
    DOORBELL("doorbell", 1.0F, "doorbell_01", "doorbell_02", "doorbell_03", "doorbell_04", "doorbell_05",
            "doorphonecalling_01", "doorphonecalling_02"),
    SIREN("siren", 4.0F, "siren"),
    SCHOOL_BELL("school_bell", 2.0F, "schoolbellringing"),
    CLOCK_BELL("clock_bell", 3.0F, "clockbellfullhour"),
    BELL("bell", 1.0F, "bellring_01", "bellring_02", "bellring_03"),
    ALARM("alarm", 2.5F, "alarm_critical");

    private final String id;
    private final float volume;
    private final String[] sounds;

    SpeakerKind(String id, float volume, String... sounds) {
        this.id = id;
        this.volume = volume;
        this.sounds = sounds;
    }

    public String id() {
        return id;
    }

    public float volume() {
        return volume;
    }

    public String[] sounds() {
        return sounds;
    }
}
