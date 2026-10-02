package com.isjbar.minercorp.vehicles.type;

import com.isjbar.minercorp.vehicles.api.OwnerKind;
import com.isjbar.minercorp.vehicles.api.VehicleOffer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Un tipo de vehiculo, leido de config.yml (tipos.&lt;id&gt;).
 *
 * @param velocidad              maxima en bloques por tick
 * @param aceleracion            bloques por tick que gana por tick al acelerar
 * @param frenado                bloques por tick que pierde por tick al frenar
 * @param giro                   grados por tick al doblar a fondo
 * @param reversa                fraccion de la maxima marcha atras
 * @param tanque                 litros
 * @param consumo                litros por bloque recorrido
 * @param velocidadSinCombustible fraccion de la maxima sin combustible
 * @param itemModel              modelo de resource pack (null = piezas de bloques)
 * @param perforacion            datos del taladro, null si no es taladro
 */
public record VehicleType(String id, String nombre, List<String> descripcion, VehicleClass clase, String modelo,
                          OwnerKind dueno, int nivelEmpresa, double precio, Material icono,
                          double velocidad, double aceleracion, double frenado, double giro, double reversa,
                          double tanque, double consumo, double velocidadSinCombustible,
                          List<String> combustibles, int carga, Map<String, Material> colores,
                          String itemModel, float escalaItemModel, DrillSpec perforacion) {

    public boolean isDrill() {
        return clase == VehicleClass.TALADRO;
    }

    public Material color(String slot, Material fallback) {
        return colores.getOrDefault(slot, fallback);
    }

    public VehicleOffer toOffer() {
        return new VehicleOffer(id, nombre, descripcion, precio, icono, dueno, nivelEmpresa);
    }

    static VehicleType load(String id, ConfigurationSection s, Logger logger) {
        VehicleClass clase = "taladro".equalsIgnoreCase(s.getString("clase")) ? VehicleClass.TALADRO : VehicleClass.TERRESTRE;
        String modelo = s.getString("modelo", clase == VehicleClass.TALADRO ? "taladro" : "camioneta").toLowerCase(Locale.ROOT);
        OwnerKind dueno = "empresa".equalsIgnoreCase(s.getString("dueno")) ? OwnerKind.EMPRESA : OwnerKind.JUGADOR;
        double velocidad = Math.max(0.01, s.getDouble("velocidad", 0.4));
        // El taladro de antes aceleraba al 15% de su maxima por tick.
        double aceleracion = Math.max(0.001, s.getDouble("aceleracion", velocidad * 0.15));
        double frenado = Math.max(0.001, s.getDouble("frenado", velocidad * 0.15));

        Map<String, Material> colores = new HashMap<>();
        ConfigurationSection c = s.getConfigurationSection("colores");
        if (c != null) {
            for (String slot : c.getKeys(false)) {
                Material m = Material.matchMaterial(c.getString(slot, ""));
                if (m != null && m.isBlock()) colores.put(slot, m);
                else logger.warning("tipos." + id + ".colores." + slot + ": no es un bloque, se usa el de fabrica.");
            }
        }

        Material icono = Material.matchMaterial(s.getString("icono", "MINECART"));
        if (icono == null || !icono.isItem()) icono = Material.MINECART;

        int carga = Math.max(0, Math.min(54, s.getInt("carga", 0)));
        carga = (carga + 8) / 9 * 9;

        String itemModel = s.getString("item-model");
        if (itemModel != null && itemModel.isBlank()) itemModel = null;

        DrillSpec perforacion = null;
        if (clase == VehicleClass.TALADRO) {
            ConfigurationSection p = s.getConfigurationSection("perforacion");
            perforacion = DrillSpec.load(p);
        }

        List<String> combustibles = new ArrayList<>();
        for (String f : s.getStringList("combustibles")) combustibles.add(f.toLowerCase(Locale.ROOT));

        return new VehicleType(id, s.getString("nombre", id), s.getStringList("descripcion"), clase, modelo, dueno,
                Math.max(1, s.getInt("nivel-empresa", 1)), Math.max(0, s.getDouble("precio", 0)), icono,
                velocidad, aceleracion, frenado, s.getDouble("giro", 4.0),
                Math.max(0, Math.min(1, s.getDouble("reversa", 0.35))),
                Math.max(1, s.getDouble("tanque", 100)), Math.max(0, s.getDouble("consumo", 0)),
                Math.max(0, Math.min(1, s.getDouble("velocidad-sin-combustible", 0))),
                combustibles, carga, colores, itemModel, (float) s.getDouble("escala-item-model", 1.0), perforacion);
    }

    public enum VehicleClass { TERRESTRE, TALADRO }
}
