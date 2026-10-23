# Şimşek Ordu Modu

## Stack
- Minecraft 1.21.1, NeoForge (MDK), Java 21, Gradle
- Mod ID: simsekordu

## Kurallar
- Sadece istenen aşamayı yap, sonraki aşamaya geçme.
- Her değişiklikten sonra `./gradlew build` çalıştır, derlenmiyorsa düzelt.
- Client/server ayrımına dikkat et: mantık sunucuda, GUI istemcide.
- Kalıcı veri NBT (addAdditionalSaveData/readAdditionalSaveData) ile.
- Kod İngilizce, oyun içi metinler lang dosyalarında (tr_tr.json, en_us.json).
- Aşama bitince değişen dosyaları ve oyunda nasıl test edileceğini kısaca yaz.

## Mimari
- entity/SimsekNpc.java: PathfinderMob; job, level, xp, rank, owner, inventory
- entity/goal/: MineGoal, HuntGoal, GuardGoal, FollowFormationGoal
- data/Job.java, data/Rank.java (enum; eşikler ve bonuslar burada)
- menu/ + client/screen/: NPC GUI
- network/: GUI → sunucu paketleri

## Rütbeler
Er(0) → Onbaşı(5) → Çavuş(10) → Teğmen(20) → Yüzbaşı(35)  [parantez: gereken seviye]

## Aşamalar
1 İskelet · 2 Veri/Rütbe · 3 GUI · 4 Madenci · 5 Avcı · 6 Asker/Ordu · 7 Eğitim · 8 Denge
