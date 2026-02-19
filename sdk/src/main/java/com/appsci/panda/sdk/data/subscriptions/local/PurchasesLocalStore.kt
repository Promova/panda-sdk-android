package com.appsci.panda.sdk.data.subscriptions.local

interface PurchasesLocalStore {

    suspend fun getPurchases(): List<PurchaseEntity>

    suspend fun getNotSentPurchases(): List<PurchaseEntity>

    fun markSynced(id: String)

    fun savePurchases(purchases: List<PurchaseEntity>)
}

class PurchasesLocalStoreImpl(private val purchaseDao: PurchaseDao) : PurchasesLocalStore {

    override suspend fun getPurchases(): List<PurchaseEntity> =
            purchaseDao.selectPurchases()

    override suspend fun getNotSentPurchases(): List<PurchaseEntity> =
            purchaseDao.selectNotSentPurchases()

    override fun markSynced(id: String) {
        purchaseDao.markSynced(id)
    }

    override fun savePurchases(purchases: List<PurchaseEntity>) {
        purchaseDao.putPurchases(purchases)
    }

}
