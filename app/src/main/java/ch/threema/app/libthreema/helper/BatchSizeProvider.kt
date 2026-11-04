package ch.threema.app.libthreema.helper

data class BatchSizeProvider(
    val contactMessageBatchSize: Int = 1000,
    val groupMessageBatchSize: Int = 1000,
    val distributionListMessageBatchSize: Int = 1000,
    val pollBatchSize: Int = 100,
)
