package brs.services.impl;

import brs.Account;
import brs.Account.AccountAsset;
import brs.Account.RewardRecipientAssignment;
import brs.AssetTransfer;
import brs.crypto.Crypto;
import brs.db.BurstIterator;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.VersionedBatchEntityTable;
import brs.db.store.AccountStore;
import brs.db.store.AssetTransferStore;
import brs.services.AccountService;
import brs.util.Convert;
import java.util.Arrays;

public class AccountServiceImpl implements AccountService {

  private final AccountStore accountStore;
  private final VersionedBatchEntityTable<Account> accountTable;
  private final LongKeyFactory<Account> accountBurstKeyFactory;

  private final AssetTransferStore assetTransferStore;

  public AccountServiceImpl(AccountStore accountStore, AssetTransferStore assetTransferStore) {
    this.accountStore = accountStore;
    this.accountTable = accountStore.getAccountTable();
    this.accountBurstKeyFactory = accountStore.getAccountKeyFactory();
    this.assetTransferStore = assetTransferStore;
  }

  @Override
  public Account getAccount(long id) {
    return id == 0 ? null : accountTable.get(accountBurstKeyFactory.newKey(id));
  }

  @Override
  public Account getAccount(long id, int height) {
    return id == 0 ? null : accountTable.get(accountBurstKeyFactory.newKey(id), height);
  }

  @Override
  public Account getAccount(byte[] publicKey) {
    final Account account = accountTable.get(accountBurstKeyFactory.newKey(getId(publicKey)));

    if (account == null) {
      return null;
    }

    if (account.getPublicKey() == null || Arrays.equals(account.getPublicKey(), publicKey)) {
      return account;
    }

    throw new RuntimeException("DUPLICATE KEY for account " + Convert.toUnsignedLong(account.getId())
        + " existing key " + Convert.toHexString(account.getPublicKey()) + " new key " + Convert.toHexString(publicKey));
  }

  @Override
  public BurstIterator<AssetTransfer> getAssetTransfers(long accountId, int from, int to) {
    return assetTransferStore.getAccountAssetTransfers(accountId, from, to);
  }

  @Override
  public BurstIterator<AccountAsset> getAssets(long accountId, int from, int to) {
    return accountStore.getAssets(from, to, accountId);
  }

  @Override
  public BurstIterator<RewardRecipientAssignment> getAccountsWithRewardRecipient(Long recipientId) {
    return accountStore.getAccountsWithRewardRecipient(recipientId);
  }

  @Override
  public BurstIterator<Account> getAllAccounts(int from, int to) {
    return accountTable.getAll(from, to);
  }

  @Override
  public Account getOrAddAccount(long id) {
    Account account = accountTable.get(accountBurstKeyFactory.newKey(id));
    if (account == null) {
      account = new Account(id);
      accountTable.insert(account);
    }
    return account;
  }

  public static long getId(byte[] publicKey) {
    byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
    return Convert.fullHashToId(publicKeyHash);
  }

  @Override
  public void flushAccountTable() {
    accountTable.finish();
  }

  @Override
  public int getCount() {
    return accountTable.getCount();
  }

}
