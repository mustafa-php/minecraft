package com.simsek.simsekordu.data;

import java.util.List;
import net.minecraft.util.RandomSource;

public final class NpcNames {
    private static final List<String> NAMES = List.of(
            "Ahmet", "Mehmet", "Mustafa", "Ali", "Hasan",
            "Hüseyin", "İbrahim", "İsmail", "Ömer", "Yusuf",
            "Osman", "Murat", "Emre", "Burak", "Kemal",
            "Cem", "Can", "Deniz", "Kaan", "Tolga",
            "Serkan", "Barış", "Onur", "Volkan", "Selim",
            "Furkan", "Berk", "Eren", "Arda", "Orhan");

    private NpcNames() {
    }

    public static String random(RandomSource random) {
        return NAMES.get(random.nextInt(NAMES.size()));
    }
}
