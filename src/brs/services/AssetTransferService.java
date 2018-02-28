package brs.services;

import brs.AssetTransfer;
import brs.Attachment;
import brs.Transaction;
import brs.db.BurstIterator;

public interface AssetTransferService {

  BurstIterator<AssetTransfer> getAssetTransfers(long assetId, int from, int to);

  BurstIterator<AssetTransfer> getAccountAssetTransfers(long accountId, long assetId, int from, int to);

  int getTransferCount(long assetId);

  int getAssetTransferCount();

  AssetTransfer addAssetTransfer(Transaction transaction, Attachment.ColoredCoinsAssetTransfer attachment);
}
