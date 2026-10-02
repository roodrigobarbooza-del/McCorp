package com.isjbar.minercorp.vehicles;

import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.vehicles.api.OwnerKind;
import com.isjbar.minercorp.vehicles.api.PurchaseResult;
import com.isjbar.minercorp.vehicles.api.VehicleOffer;
import com.isjbar.minercorp.vehicles.api.VehiclesAPI;
import com.isjbar.minercorp.vehicles.garage.StoredVehicle;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Implementacion de {@link VehiclesAPI}. */
public class VehiclesService implements VehiclesAPI {

    private final VehiclesPlugin plugin;

    public VehiclesService(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<VehicleOffer> catalog() {
        return plugin.types().all().stream().map(VehicleType::toOffer).toList();
    }

    @Override
    public PurchaseResult purchase(Player buyer, String typeId) {
        Optional<VehicleType> typeOpt = plugin.types().get(typeId);
        if (typeOpt.isEmpty()) return PurchaseResult.TIPO_INVALIDO;
        VehicleType type = typeOpt.get();

        UUID account;
        if (type.dueno() == OwnerKind.EMPRESA) {
            Optional<Company> company = plugin.mining().companies().getByMember(buyer.getUniqueId());
            if (company.isEmpty()) return PurchaseResult.SIN_EMPRESA;
            if (!plugin.mining().obras().sedeLista(company.get())) return PurchaseResult.SEDE_SIN_TERMINAR;
            if (company.get().getLevel() < type.nivelEmpresa()) return PurchaseResult.NIVEL_INSUFICIENTE;
            account = company.get().getId();
        } else {
            account = buyer.getUniqueId();
        }
        if (!plugin.economy().withdraw(account, type.precio())) return PurchaseResult.SIN_SALDO;
        plugin.garage().add(account, new StoredVehicle(type.id(), 0, null));
        plugin.garage().save();
        return PurchaseResult.OK;
    }

    @Override
    public boolean giveToGarage(UUID ownerId, String typeId) {
        Optional<VehicleType> type = plugin.types().get(typeId);
        if (type.isEmpty()) return false;
        plugin.garage().add(ownerId, new StoredVehicle(type.get().id(), 0, null));
        plugin.garage().save();
        return true;
    }

    @Override
    public void registerFuel(String id, String name, Predicate<ItemStack> matcher, double litersPerItem) {
        plugin.fuels().register(id, name, matcher, litersPerItem);
        plugin.getLogger().info("Combustible registrado por otro plugin: " + name + " (" + id + ")");
    }
}
