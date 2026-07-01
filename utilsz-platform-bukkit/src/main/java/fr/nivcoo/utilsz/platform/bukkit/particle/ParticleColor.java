package fr.nivcoo.utilsz.platform.bukkit.particle;

import org.bukkit.Color;

@SuppressWarnings("unused")
public final class ParticleColor {

    public int red = 255;
    public int green = 255;
    public int blue = 255;

    public ParticleColor() {
    }

    public ParticleColor(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public Color color() {
        return Color.fromRGB(Math.clamp(red, 0, 255), Math.clamp(green, 0, 255), Math.clamp(blue, 0, 255));
    }
}
