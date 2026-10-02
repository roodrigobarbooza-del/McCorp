package com.isjbar.minercorp.pack.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Forma comun de ponerle a un item o a un ItemDisplay un modelo del resource
 * pack de McCorp. Los ids son la ruta dentro de assets/mccorp/items sin el
 * .json: "resources/gasolina" es assets/mccorp/items/resources/gasolina.json.
 * Tambien se acepta el id completo ("mccorp:resources/gasolina").
 *
 * <p>Si el pack esta apagado ({@link #activo()} es false) no se toca nada y el
 * item queda con su aspecto vanilla, para que nadie vea modelos faltantes.
 *
 * <p>Esta clase vive en el jar de MinerCorp-Pack: un plugin que la use pone
 * {@code softdepend: [MinerCorp-Pack]} y la llama solo si el plugin esta
 * habilitado (ver resourcepack/LEEME.md).
 */
public final class Modelos {

    public static final String NAMESPACE = "mccorp";

    private static volatile boolean activo;
    private static Logger logger = Logger.getLogger("MinerCorp-Pack");
    private static final Set<String> invalidos = ConcurrentHashMap.newKeySet();

    private Modelos() {
    }

    /** True si los jugadores reciben el pack, o sea si los modelos se van a ver. */
    public static boolean activo() {
        return activo;
    }

    /** Solo para MinerCorp-Pack. */
    public static void configurar(boolean estaActivo, Logger log) {
        activo = estaActivo;
        if (log != null) logger = log;
    }

    /** "resources/gasolina" -> mccorp:resources/gasolina. Null si el id no es valido. */
    public static NamespacedKey clave(String id) {
        if (id == null || id.isBlank()) return null;
        String limpio = id.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(limpio.contains(":") ? limpio : NAMESPACE + ":" + limpio);
        if (key == null && invalidos.add(id)) {
            logger.warning("Id de modelo invalido: '" + id + "' (solo minusculas, numeros, _ - . y /)");
        }
        return key;
    }

    /** Le pone el modelo al item (si el pack esta activo) y lo devuelve. */
    public static ItemStack aplicar(ItemStack item, String id) {
        if (!activo || item == null || item.getType().isAir()) return item;
        NamespacedKey key = clave(id);
        if (key != null) item.editMeta(meta -> meta.setItemModel(key));
        return item;
    }

    /** Item nuevo con el modelo puesto; sin pack se ve como {@code base}. */
    public static ItemStack item(Material base, String id) {
        return aplicar(new ItemStack(base), id);
    }

    /**
     * Pone el modelo en un ItemDisplay ya creado, escalado igual en los tres
     * ejes. Sin pack muestra {@code base}.
     */
    public static void aplicar(ItemDisplay display, String id, Material base, float escala) {
        display.setItemStack(item(base, id));
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(escala, escala, escala), new Quaternionf()));
    }

    /**
     * Crea un ItemDisplay con el modelo en {@code donde}. Los modelos grandes
     * (vehiculos, maquinas) se arman en Blockbench dentro del limite de 3x3x3
     * bloques y se agrandan con {@code escala}.
     */
    public static ItemDisplay mostrar(Location donde, String id, Material base, float escala) {
        return donde.getWorld().spawn(donde, ItemDisplay.class, d -> {
            aplicar(d, id, base, escala);
        });
    }
}
