import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localPath: String, // Path atau URI string ke file gambar lokal
    val tempatWisataId: String? = null // Opsional: untuk menghubungkan ke Firestore ID (nama tempat)
)