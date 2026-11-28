package com.voxelsandbox;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 世界存档：只持久化玩家改动过的方块、玩家位置/朝向、时间与选中方块。
 * 地形本身由固定种子重新生成，因此存档体积很小。
 */
public final class SaveGame {

    private static final int MAGIC = 0x56584331; // "VXC1"
    private static final int FORMAT = 1;
    private static final String FILE_NAME = "savegame.dat";
    private static final int MAX_EDITS = 4_000_000;

    public static final class Data {
        public final Map<Long, Byte> edits;
        public final float dayTime;
        public final int selectedSlot;
        public final float px, py, pz, yaw, pitch;

        Data(Map<Long, Byte> edits, float dayTime, int selectedSlot,
             float px, float py, float pz, float yaw, float pitch) {
            this.edits = edits;
            this.dayTime = dayTime;
            this.selectedSlot = selectedSlot;
            this.px = px; this.py = py; this.pz = pz;
            this.yaw = yaw; this.pitch = pitch;
        }
    }

    private SaveGame() {}

    public static Data load(Context context) {
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (!f.exists()) return null;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC) return null;
            if (in.readInt() != FORMAT) return null;
            float dayTime = in.readFloat();
            int slot = in.readInt();
            float px = in.readFloat();
            float py = in.readFloat();
            float pz = in.readFloat();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            int n = in.readInt();
            if (n < 0 || n > MAX_EDITS) return null;
            Map<Long, Byte> edits = new HashMap<>(Math.max(16, n * 4 / 3 + 1));
            for (int i = 0; i < n; i++) {
                long key = in.readLong();
                byte id = in.readByte();
                edits.put(key, id);
            }
            if (Float.isNaN(px) || Float.isNaN(py) || Float.isNaN(pz)) return null;
            return new Data(edits, dayTime, slot, px, py, pz, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean save(Context context, World world, Player player,
                               SkyCycle sky, int selectedSlot) {
        File dir = context.getFilesDir();
        File tmp = new File(dir, FILE_NAME + ".tmp");
        File dst = new File(dir, FILE_NAME);
        Map<Long, Byte> edits = world.copyEdits();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeFloat(sky.dayTime);
            out.writeInt(selectedSlot);
            out.writeFloat(player.x);
            out.writeFloat(player.y);
            out.writeFloat(player.z);
            out.writeFloat(player.yaw);
            out.writeFloat(player.pitch);
            out.writeInt(edits.size());
            for (Map.Entry<Long, Byte> e : edits.entrySet()) {
                out.writeLong(e.getKey());
                out.writeByte(e.getValue());
            }
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        // 先删旧文件再改名，保证读到的一定是完整存档
        dst.delete();
        return tmp.renameTo(dst);
    }
}
