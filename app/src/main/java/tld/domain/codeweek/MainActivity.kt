package tld.domain.codeweek

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.apollographql.apollo.internal.util.Cancelable
import tld.domain.codeweek.api.ChatRepo

class MainActivity : AppCompatActivity() {

    var running: Cancelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val chatVerbindung = ChatRepo()

        val chatText2 = findViewById<TextView>(R.id.chattext2)
        val nachrichtenEingabe: EditText = findViewById(R.id.input_text)
        val namensEingabe: EditText = findViewById(R.id.input_name)
        val nachrichtenZaehler: TextView = findViewById(R.id.message_counter)
        val buchstabenZaehler: TextView = findViewById(R.id.text_counter)
        val sendenKnopf: Button = findViewById(R.id.action_send)

        val zeitFormatierer = DateFormat.getTimeFormat(this)
        chatText2.movementMethod = ScrollingMovementMethod()

        // Zeigt die Nachrichten an
        chatVerbindung.observeMessagesAsync { messages ->
            val filteredMessages = messages
            var addierer = ""
            for (msg in filteredMessages) {
                var name = msg.author
                if (name.isBlank()) name = "<Unbekannt>"

                val nachricht = msg.content

                val zeit = zeitFormatierer.format(msg.created)

                val output = "$zeit - $name: $nachricht"
                addierer = addierer + "\n" + output
            }

            chatText2.text = addierer

            nachrichtenZaehler.text = "${filteredMessages.size} Nachrichten"
        }

        nachrichtenEingabe.addTextChangedListener {
            buchstabenZaehler.text = "${it?.length} Zeichen"
        }

        // Schickt Nachrichten ab
        sendenKnopf.setOnClickListener {
            val name = namensEingabe.text.toString()
            val msg = nachrichtenEingabe.text.toString()
            chatVerbindung.sendMessageAsync(name, msg)
            nachrichtenEingabe.setText("")
        }


        val einstellungen = getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
        // Beim Laden der App den gespeicherten Namen wiederherstellen
        val alterName = einstellungen.getString("name", null)
        if (alterName != null) {
            namensEingabe.setText(alterName)
        }

        // Namensänderungen auf dem Handy speichern
        namensEingabe.addTextChangedListener {
            einstellungen.edit().putString("name", namensEingabe.text.toString()).apply()
        }
    }


    override fun onDestroy() {
        running?.cancel()
        super.onDestroy()
    }
}
