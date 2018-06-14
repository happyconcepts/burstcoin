package brs.unconfirmedtransactions;

import static brs.Attachment.ORDINARY_PAYMENT;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import brs.Account;
import brs.BlockchainImpl;
import brs.Burst;
import brs.BurstException.NotCurrentlyValidException;
import brs.BurstException.NotValidException;
import brs.BurstException.ValidationException;
import brs.Constants;
import brs.Transaction;
import brs.Transaction.Builder;
import brs.TransactionType;
import brs.common.Props;
import brs.common.TestConstants;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.VersionedBatchEntityTable;
import brs.db.store.AccountStore;
import brs.fluxcapacitor.FeatureToggle;
import brs.fluxcapacitor.FluxCapacitor;
import brs.services.PropertyService;
import brs.services.TimeService;
import brs.services.impl.TimeServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class UnconfirmedTransactionStoreTest {

  private BlockchainImpl mockBlockChain;

  private AccountStore accountStoreMock;
  private VersionedBatchEntityTable<Account> accountTableMock;
  private LongKeyFactory<Account> accountBurstKeyFactoryMock;

  private TimeService timeService = new TimeServiceImpl();
  private UnconfirmedTransactionStore t;

  @Before
  public void setUp() {
    mockStatic(Burst.class);

    final PropertyService mockPropertyService = mock(PropertyService.class);
    when(mockPropertyService.getInt(eq(Props.DB_MAX_ROLLBACK))).thenReturn(1440);
    when(Burst.getPropertyService()).thenReturn(mockPropertyService);
    when(mockPropertyService.getInt(eq(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS), eq(8192))).thenReturn(8192);

    mockBlockChain = mock(BlockchainImpl.class);
    when(Burst.getBlockchain()).thenReturn(mockBlockChain);

    accountStoreMock = mock(AccountStore.class);
    accountTableMock = mock(VersionedBatchEntityTable.class);
    accountBurstKeyFactoryMock = mock(LongKeyFactory.class);
    when(accountStoreMock.getAccountTable()).thenReturn(accountTableMock);
    when(accountStoreMock.getAccountKeyFactory()).thenReturn(accountBurstKeyFactoryMock);

    final Account mockAccount = mock(Account.class);
    final BurstKey mockAccountKey = mock(BurstKey.class);
    when(accountBurstKeyFactoryMock.newKey(eq(123L))).thenReturn(mockAccountKey);
    when(accountTableMock.get(eq(mockAccountKey))).thenReturn(mockAccount);
    when(mockAccount.getUnconfirmedBalanceNQT()).thenReturn(Constants.MAX_BALANCE_NQT);

    FluxCapacitor mockFluxCapacitor = mock(FluxCapacitor.class);
    when(mockFluxCapacitor.isActive(eq(FeatureToggle.PRE_DYMAXION))).thenReturn(true);
    when(mockFluxCapacitor.isActive(eq(FeatureToggle.PRE_DYMAXION), anyInt())).thenReturn(true);

    TransactionType.init(mockBlockChain, mockFluxCapacitor, null, null, null, null, null, null);

    t = new UnconfirmedTransactionStore(timeService, mockPropertyService, accountStoreMock);
  }

  @DisplayName("The amount of unconfirmed transactions exceeds max size, when adding another the cache size stays the same")
  @Test
  public void numberOfUnconfirmedTransactionsExceedsMaxSizeAddAnotherThenCacheSizeStaysMaxSize() throws ValidationException {

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 8192; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction);
    }

    assertEquals(8192, t.getAll().size());
    assertNotNull(t.get(1L));

    final Transaction oneTransactionTooMany =
        new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 9999, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
            .id(8193L).senderId(123L).build();
    oneTransactionTooMany.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(oneTransactionTooMany);

    assertEquals(8192, t.getAll().size());
    assertNull(t.get(1L));
  }

  @DisplayName("The amount of unconfirmed transactions exceeds max size, when adding a group of others the cache size stays the same")
  @Test
  public void numberOfUnconfirmedTransactionsExceedsMaxSizeAddAGroupOfOthersThenCacheSizeStaysMaxSize() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 8192; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction);
    }

    assertEquals(8192, t.getAll().size());
    assertNotNull(t.get(1L));
    assertNotNull(t.get(2L));
    assertNotNull(t.get(3L));

    final List<Transaction> groupOfExtras = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final Transaction extraTransaction =
          new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 9999, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
              .id(8193 + i).senderId(123L).build();
      extraTransaction.sign(TestConstants.TEST_SECRET_PHRASE);
      groupOfExtras.add(extraTransaction);
    }

    t.put(groupOfExtras);

    assertEquals(8192, t.getAll().size());
    assertNull(t.get(1L));
    assertNull(t.get(2L));
    assertNull(t.get(3L));

    assertNotNull(t.get(8123L));
    assertNotNull(t.get(8124L));
    assertNotNull(t.get(8125L));
  }

  @DisplayName("Old transactions get removed from the cache when they are expired")
  @Test
  public void transactionGetsRemovedWhenExpired() throws ValidationException, InterruptedException {
    final int deadlineWithin2Seconds = timeService.getEpochTime() - 29998;
    final Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 500, 735000, deadlineWithin2Seconds, (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(123L).build();

    transaction.sign(TestConstants.TEST_SECRET_PHRASE);

    t.put(transaction);

    assertNotNull(t.get(1L));

    Thread.sleep(3000);

    assertNull(t.get(1L));
  }

  @DisplayName("Old transactions get removed from the cache when they are expired when using the foreach method on them")
  @Test
  public void transactionGetsRemovedWhenExpiredWhenRunningForeach() throws ValidationException, InterruptedException {
    final int deadlineWithin2Seconds = timeService.getEpochTime() - 29998;

    for (int i = 0; i < 10; i++) {
      final Transaction transaction = new Transaction.Builder((byte) i, TestConstants.TEST_PUBLIC_KEY_BYTES, 500, 735000, deadlineWithin2Seconds, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();

      transaction.sign(TestConstants.TEST_SECRET_PHRASE);

      t.put(transaction);
    }

    assertNotNull(t.get(1L));

    Thread.sleep(3000);

    t.forEach(t -> fail("No transactions should be left to run the foreach on"));
  }

  @DisplayName("The unconfirmed transaction gets denied in case the account is unknown")
  @Test(expected = NotCurrentlyValidException.class)
  public void unconfirmedTransactionGetsDeniedForUnknownAccount() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(124L).build();
    transaction.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(transaction);
  }

  @DisplayName("The unconfirmed transaction gets denied in case the account does not have enough unconfirmed balance")
  @Test(expected = NotCurrentlyValidException.class)
  public void unconfirmedTransactionGetsDeniedForNotEnoughUnconfirmedBalance() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, Constants.MAX_BALANCE_NQT, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(123L).build();
    transaction.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(transaction);
  }

  @DisplayName("When adding the same unconfirmed transaction, the first one gets refunded")
  @Test
  public void addingNewUnconfirmedTransactionWithSameIDRefundsTheFirstOne() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    Builder transactionBuilder = new Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, Constants.MAX_BALANCE_NQT - 100000, timeService.getEpochTime() + 50000,
        (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(123L);

    Transaction transaction1 = transactionBuilder.build();
    transaction1.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(transaction1);

    Transaction transaction2 = transactionBuilder.build();
    transaction2.sign(TestConstants.TEST_SECRET_PHRASE);

    t.put(transaction2);
  }

  @Test
  public void transactionsCanBeRetrievedBasedOnTheTimestampThatTheyGetAdded() throws ValidationException {
    final long momentOne = timeService.getEpochTimeMillis();

    for (int i = 1; i <= 5; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction);
    }

    final long momentTwo = timeService.getEpochTimeMillis();

    for (int i = 1; i <= 5; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction);
    }

    final long momentThree = timeService.getEpochTimeMillis();

    assertEquals(10, t.getAllSince(momentOne).size());
    assertEquals(5, t.getAllSince(momentTwo).size());
    assertEquals(0, t.getAllSince(momentThree).size());
  }
}
