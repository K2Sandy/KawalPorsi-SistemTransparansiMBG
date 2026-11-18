# KawalPorsi

Aplikasi web lokal untuk demonstrasi **transparansi alokasi bahan pangan Dapur MBG**. Petugas mencatat belanja dan porsi, auditor memeriksa transaksi yang ditandai, admin memelihara standar, dan masyarakat melihat hasil yang sudah dapat dipublikasikan. Antarmuka menggunakan bahasa Indonesia.

## Menjalankan aplikasi

1. Ekstrak seluruh ZIP ke folder yang dapat ditulisi, misalnya folder Documents.
2. Siapkan Java 21 atau lebih baru. JDK 25 sesuai lingkungan yang direncanakan dalam proposal.
3. Di Windows, klik dua kali **Jalankan-Windows.bat**. Di macOS atau Linux, buka terminal di folder hasil ekstrak dan jalankan `sh Jalankan.sh`. Perintah langsung untuk semua sistem operasi adalah `java -jar KawalPorsi.jar`.
4. Setelah terminal menampilkan **Started FlowApplication**, buka **http://localhost:8080**. Gunakan Chrome, Edge, atau Firefox. Tekan Ctrl+C di terminal untuk menutup aplikasi.

JAR memuat Vaadin Flow 25.3.0, H2 2.5.252, Spring Boot, ikon, font, dan antarmuka produksi. Tidak perlu Maven, Node.js, internet, atau server database terpisah untuk menjalankan JAR. Jika port sudah dipakai, jalankan `java -jar KawalPorsi.jar --server.port=8081` lalu buka http://localhost:8081.

## Akses demo

Pilih peran di layar masuk. Kode pengguna contoh terisi otomatis. Kata sandi untuk ketiga akun adalah **KawalPorsi2026!**

| Peran | Kode pengguna | Cakupan |
|---|---|---|
| Petugas Dapur | SPPG-AB-0107 | SPPG Lambaro 01 |
| Auditor | 198804122014 | Aceh Besar dan Banda Aceh |
| Admin | 198203152008 | Seluruh dapur demo |
| Masyarakat | Buka **Lihat dasbor publik** | Tanpa akun |

Lima dapur, tiga wilayah, enam bahan dan 30 transaksi contoh tersedia saat aplikasi pertama kali dijalankan. Basis data H2 disimpan di folder `data/` dalam direktori kerja. Untuk memakai data yang sama pada sesi berikutnya, jalankan dari folder yang sama. Data dan akun demo bersifat simulasi. Server bawaan mendengarkan pada komputer lokal.

## Skenario presentasi

Gunakan tiga profil browser atau jendela privat terpisah untuk membuka akun petugas, auditor dan publik secara bersamaan.

1. **Petugas → Catat belanja.** Isi menu “Nasi uji”, 100 porsi, bahan Beras medium, volume 50 kg dan total harga Rp1.234.500. Aplikasi menghitung Rp12.345 per porsi. Tambahkan nota JPG, PNG, atau PDF, simpan draf, lalu kirim untuk diaudit.
2. **Auditor → Antrean anomali.** Buka transaksi yang baru dikirim. Terhadap ISB awal Aceh Besar sebesar Rp10.000, deviasi biayanya +23,45%. Auditor dapat melihat rincian bahan, harga acuan saat pengiriman, nota dan jejak audit. Pilih **Minta klarifikasi**, tulis pertanyaan, lalu simpan.
3. **Petugas → Klarifikasi.** Buka transaksi, pilih penyebab, tulis penjelasan dan kirim. Auditor akan melihat jawaban dan lampiran pada detail transaksi.
4. **Auditor → Detail transaksi.** Pilih **Setujui dan buka klaim** dengan alasan. Keputusan penolakan juga tersedia. Semua perubahan status tersimpan dalam riwayat.
5. **Publik → Daftar dapur → SPPG Lambaro 01.** Biaya transaksi yang sedang ditinjau tampil sebagai tanda pisah. Setelah auditor menyetujuinya, Rp12.345 terlihat di riwayat dan ikut dalam rata-rata. CSV menerapkan aturan tampilan yang sama.
6. **Admin → ISB daerah.** Ubah standar Aceh Besar dengan alasan. Buka kembali transaksi tadi. Standar yang tersimpan pada transaksi lama tetap Rp10.000.
7. **Publik → Laporkan kejanggalan.** Kirim uraian untuk sebuah dapur. Auditor wilayahnya dapat membuka laporan di menu **Laporan warga** dan menandainya sudah ditinjau.

Petugas dapat mengirim satu catatan per dapur per hari. Setelah pengiriman, koreksi dan penjelasan masuk melalui alur klarifikasi. Untuk mengulang demo beberapa kali tanpa mengubah riwayat sebelumnya, gunakan basis data lain, misalnya `java -Dkawal.db=jdbc:h2:file:./data/demo-kedua -jar KawalPorsi.jar`.

## Mesin audit dan implementasi

- **Biaya per porsi** dihitung dari jumlah harga seluruh baris belanja dibagi jumlah porsi. Deviasi terhadap ISB dihitung dari selisih biaya per porsi dan ISB, dibagi ISB, lalu dikalikan 100.
- **Harga satuan** membandingkan jumlah rupiah per baris dengan volume dikali harga acuan. Secara default kedua strategi aktif. Dengan ambang 5%, tepat +5% atau −5% masih lolos uji tersebut. Satu strategi yang melewati batas sudah cukup untuk menandai transaksi. Pemeriksaan memakai angka sebelum pembulatan tampilan.
- **Pembekuan klaim** merupakan status di dalam sistem. Saat transaksi ditandai, Observer mencatat aktivitas dan membuat peringatan dalam transaksi database yang sama. Auditor yang sedang membuka aplikasi menerima pemberitahuan pada pemeriksaan berikutnya (setiap empat detik). Keputusan auditor menentukan status publik.
- **Akses** dibatasi berdasarkan peran, dapur, dan wilayah. Nota dan klarifikasi hanya tersedia bagi petugas pemilik, auditor wilayah, dan admin. Kata sandi disimpan dengan BCrypt. Ringkasan publik menghitung rata-rata tertimbang dari belanja dan porsi transaksi yang disetujui.
- **Batas implementasi.** Versi ini mengikuti judul, Bab 3.3–3.5, dan layar Figma tentang audit belanja MBG. Tabel 3.1 memuat lingkup terpisah mengenai pelacakan nutrisi pribadi melalui foto. Modul tersebut tidak termasuk dalam versi audit. Pemberitahuan berjalan di aplikasi. Pengiriman email, pencairan dana sebenarnya, integrasi data pemerintah dan pengujian SUS dengan responden belum dilakukan.

## Membuka dan membangun kode

Buka **pom.xml** sebagai proyek Maven di IntelliJ IDEA. Gunakan JDK 21+ dan Maven 3.9+. Dari folder proyek:

```sh
mvn clean package
java -jar target/kawalporsi-1.0.0.jar
```

Build pertama mengunduh dependensi Maven. `mvn test` menjalankan pengujian batas audit, harga satuan, penguncian transaksi, histori acuan dan penyimpanan H2. Untuk menjalankan melalui IntelliJ sesudah build, gunakan JAR produksi atau konfigurasi Maven di atas.

| Bagian | Lokasi dalam proyek |
|---|---|
| Titik masuk Vaadin dan rute antarmuka | src/main/java/id/kawalporsi/FlowApplication.java, KawalView.java |
| Pengendali formulir, otorisasi dan sesi | WorkflowServlet.java, Application.java, Service.java |
| Model dan pola desain | BahanPangan.java, Audit.java |
| Halaman petugas, auditor, admin, publik | Pages.java, Entry.java, Staff.java, Admin.java, PublicPages.java |
| Transaksi JDBC, data awal dan skema | Database.java, src/main/resources/schema.sql |
| CSS, interaksi browser, ikon dan font | src/main/resources/META-INF/resources/ |
| Pengujian | src/test/java/id/kawalporsi/AuditTest.java |

Sumber desain: [Figma KawalPorsi, layar pelengkap 13–19](https://www.figma.com/design/zv1rhq9oSEknM1mE3qpSTU?node-id=36-2713).
# KawalPorsi-
