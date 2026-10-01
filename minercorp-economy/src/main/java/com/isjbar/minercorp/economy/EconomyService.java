package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.EconomyAPI;

import java.util.UUID;

class EconomyService implements EconomyAPI {

    private final AccountManager accounts;

    EconomyService(AccountManager accounts) {
        this.accounts = accounts;
    }

    @Override
    public double getBalance(UUID accountId) {
        return accounts.getBalance(accountId);
    }

    @Override
    public void deposit(UUID accountId, double amount) {
        accounts.deposit(accountId, amount);
    }

    @Override
    public boolean withdraw(UUID accountId, double amount) {
        return accounts.withdraw(accountId, amount);
    }

    @Override
    public boolean transfer(UUID fromAccountId, UUID toAccountId, double amount) {
        return accounts.transfer(fromAccountId, toAccountId, amount);
    }
}
