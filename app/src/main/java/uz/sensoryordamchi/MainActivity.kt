package uz.sensoryordamchi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Oddiy ishga tushirish ekrani. Hamma narsa chap tomonga sig'adi
 * (o'ng 35% bo'sh qoldirilgan), chunki o'ng qismi sensori ishlamaydi.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dm = resources.displayMetrics
        val pad = (16 * dm.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, (dm.widthPixels * 0.35f).toInt(), pad)
        }

        val title = TextView(this).apply {
            text = "Sensor yordamchi"
            textSize = 22f
        }

        val info = TextView(this).apply {
            textSize = 15f
            setPadding(0, pad, 0, pad)
            text = "Yoqish:\n" +
                "1) Pastdagi tugmani bosing.\n" +
                "2) \"Yuklab olingan ilovalar\" (yoki \"O'rnatilgan xizmatlar\") ichidan \"Sensor yordamchi\"ni toping.\n" +
                "3) Uni yoqing va ruxsatni tasdiqlang.\n" +
                "4) Ekranning chap pastida touchpad paneli paydo bo'ladi.\n\n" +
                "Boshqarish:\n" +
                "• Panelda bir barmoq bilan suring: kursor harakatlanadi.\n" +
                "• Panelga bir marta tegib oling: kursor turgan joy bosiladi.\n" +
                "• Panelni bosib turing: uzoq bosish.\n" +
                "• Ikki barmoq bilan yuqoriga/pastga suring: scroll.\n" +
                "• Orqaga, Uy, Ilovalar tugmalari tizim tugmalarini bosadi.\n" +
                "• ▲ / ▼ tugmalari kursor turgan joyda scroll qiladi.\n" +
                "• \"Yig'ish\" paneli kichik doira tugmaga aylantiradi."
        }

        val button = Button(this).apply {
            text = "Maxsus imkoniyatlar sozlamasini ochish"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(title)
        root.addView(info)
        root.addView(button)

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
