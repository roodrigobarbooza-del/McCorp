package com.isjbar.minercorp.resources.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Forma de cada maquina, leida de modelos/maquinas.json (dentro del jar). Ese
 * archivo lo genera tools/recursos/generar.py junto con los modelos del
 * resource pack, asi los dos modos de dibujo (bloques vanilla o pack) salen de
 * la misma geometria.
 *
 * Sistema local: el bloque del panel ocupa [0,1]^3, el jugador lo coloca
 * mirando hacia +Z y el cuerpo de la maquina queda detras, en z >= 2.
 */
public record MachineGeometry(List<int[]> cells, Vector3f label, List<Vector3f> smoke, List<Vector3f> flames,
                              List<Vector3f> sparks, String sound, int soundEvery, float soundVolume,
                              float soundPitch, List<Part> parts) {

    /** Una caja del modo sin pack: bloque vanilla, esquinas y rotacion opcional de 45 grados. */
    public record Box(Vector3f from, Vector3f to, Material block, char axis, Vector3f origin) {
    }

    /**
     * Una pieza: todas sus cajas se mueven juntas.
     *
     * @param anim  null, "giro-y", "giro-x", "balanceo-x", "vaiven-y" o "luz" (solo se ve trabajando)
     * @param model modelo de item del pack (null si la pieza no tiene), con su centro y escala k
     */
    public record Part(String id, String anim, Vector3f pivot, Map<String, Double> args, List<Box> boxes,
                       NamespacedKey model, Vector3f center, float k) {

        public double arg(String key, double fallback) {
            return args.getOrDefault(key, fallback);
        }
    }

    public static Map<String, MachineGeometry> load(Plugin plugin) {
        Map<String, MachineGeometry> out = new HashMap<>();
        try (InputStream in = plugin.getResource("modelos/maquinas.json")) {
            if (in == null) throw new IllegalStateException("falta modelos/maquinas.json en el jar");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                out.put(e.getKey(), parse(e.getValue().getAsJsonObject()));
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudieron leer los modelos de las maquinas", ex);
        }
        return out;
    }

    private static MachineGeometry parse(JsonObject o) {
        List<int[]> cells = new ArrayList<>();
        for (JsonElement c : o.getAsJsonArray("colision")) {
            JsonArray a = c.getAsJsonArray();
            cells.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
        }
        JsonObject efectos = o.getAsJsonObject("efectos");
        JsonObject sonido = o.getAsJsonObject("sonido");
        List<Part> parts = new ArrayList<>();
        for (JsonElement pe : o.getAsJsonArray("partes")) {
            JsonObject p = pe.getAsJsonObject();
            List<Box> boxes = new ArrayList<>();
            for (JsonElement be : p.getAsJsonArray("cajas")) {
                JsonObject b = be.getAsJsonObject();
                Material material = Material.matchMaterial(b.get("b").getAsString());
                if (material == null || !material.isBlock()) material = Material.GRAY_CONCRETE;
                char axis = 0;
                Vector3f origin = null;
                if (b.has("rot")) {
                    JsonObject r = b.getAsJsonObject("rot");
                    axis = r.get("eje").getAsString().charAt(0);
                    origin = vec(r.getAsJsonArray("origen"));
                }
                boxes.add(new Box(vec(b.getAsJsonArray("de")), vec(b.getAsJsonArray("a")), material, axis, origin));
            }
            Map<String, Double> args = new HashMap<>();
            if (p.has("args")) {
                for (Map.Entry<String, JsonElement> a : p.getAsJsonObject("args").entrySet()) {
                    args.put(a.getKey(), a.getValue().getAsDouble());
                }
            }
            NamespacedKey model = p.has("modelo") ? NamespacedKey.fromString(p.get("modelo").getAsString()) : null;
            parts.add(new Part(p.get("id").getAsString(),
                    p.has("anim") && !p.get("anim").isJsonNull() ? p.get("anim").getAsString() : null,
                    vec(p.getAsJsonArray("pivote")), args, boxes, model,
                    p.has("centro") ? vec(p.getAsJsonArray("centro")) : new Vector3f(),
                    p.has("k") ? p.get("k").getAsFloat() : 1f));
        }
        return new MachineGeometry(cells, vec(o.getAsJsonArray("cartel")),
                points(efectos, "humo"), points(efectos, "llama"), points(efectos, "chispas"),
                sonido.get("nombre").getAsString(), sonido.get("cada").getAsInt(),
                sonido.get("volumen").getAsFloat(), sonido.get("tono").getAsFloat(), parts);
    }

    private static List<Vector3f> points(JsonObject o, String key) {
        List<Vector3f> out = new ArrayList<>();
        if (o != null && o.has(key)) {
            for (JsonElement e : o.getAsJsonArray(key)) out.add(vec(e.getAsJsonArray()));
        }
        return out;
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
}
