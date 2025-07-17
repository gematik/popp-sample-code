/*
 * Copyright (Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.refpopp.popp_module

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gematik.refpopp.popp_module.ui.theme.PoppModuleAppTheme
import de.gematik.security.nfcCard.NfcCard
import de.gematik.security.nfcCard.NfcCardTerminals
import de.gematik.security.nfcCard.NfcHealthCardProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.security.Security
import javax.smartcardio.CardException
import javax.smartcardio.CardTerminals
import javax.smartcardio.TerminalFactory

class EgkAuthActivity : ComponentActivity() {

    companion object {
        private val TAG = EgkAuthActivity::class.java.name

        init {
            Security.addProvider(NfcHealthCardProvider())
        }

        const val PROGRESS_WAIT_FOR_CARD = "Wait for card"
        const val PROGRESS_CONNECT_TO_CARD = "Connect to card"
        const val PROGRESS_NO_CARD_PRESENT = "No card present"
        const val PROGRESS_DONE = "Done"
    }

    private var can: String? = null
    private var pin: String? = null

    private var nfcCardTerminals: NfcCardTerminals? = null
    private var nfcCard: NfcCard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoppModuleAppTheme {
                AuthenticationDialog()
            }
        }

        try {
            nfcCardTerminals =
                TerminalFactory.getDefault(this).terminals() as NfcCardTerminals
        } catch (ex: ClassCastException) {
            Toast.makeText(this, "no NFC card terminals\n", Toast.LENGTH_LONG).show()
            Log.e(TAG, "class cast exception", ex)
            Log.e(
                TAG,
                "no NFC card terminals - context = $this; terminals = $nfcCardTerminals"
            )
            for (p in Security.getProviders()) {
                Log.e(TAG, p.name)
            }
        }

    }

    @Composable
    @Preview
    private fun AuthenticationDialog() {
        val canValue = remember { mutableStateOf(TextFieldValue()) }
        val pinValue = remember { mutableStateOf(TextFieldValue()) }

        val progressPrompt = remember { mutableStateOf("Please tap card!") }
        val progressText = remember { mutableStateOf("") }
        val progress = remember { mutableStateOf(0.0f) }

        val errorText = remember { mutableStateOf("") }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header()
            if (progress.value != 0.0f && errorText.value.isEmpty()) {
                Progress(progressPrompt, progressText, progress)
            } else {
                CanField(canValue)
                PinField(pinValue)
                SubmitButton(canValue, pinValue, progressPrompt, progressText, progress, errorText)
                if (!errorText.value.isEmpty()) {
                    ErrorText(errorText)
                }
            }
        }
    }

    @Composable
    private fun Header() {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_smartcardgematik),
                contentDescription = "Gematik Logo"
            )

            Text(
                text = "Authentifizierung mit eGK",
                fontFamily = FontFamily.SansSerif,
                fontSize = 22.sp,
                modifier = Modifier.padding(20.dp)
            )
        }

    }

    @Composable
    private fun Progress(progressPrompt: MutableState<String>, progressText: MutableState<String>, progress: MutableState<Float>) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = progressPrompt.value,
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .width(300.dp)
                    .padding(vertical = 20.dp),
                progress = progress.value
            )

            Text(
                text = progressText.value,
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp
            )
        }

    }

    @Composable
    private fun CanField(inputvalue: MutableState<TextFieldValue>) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "CAN:",
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp,
                modifier = Modifier
                    .width(50.dp)
                    .padding(vertical = 20.dp)
            )

            TextField(
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation =  PasswordVisualTransformation(),
                value = inputvalue.value,
                onValueChange = { inputvalue.value = it }
            )
        }

    }

    @Composable
    private fun PinField(inputvalue: MutableState<TextFieldValue>) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "PIN:",
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp,
                modifier = Modifier
                    .width(50.dp)
                    .padding(vertical = 20.dp)
            )

            TextField(
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation =  PasswordVisualTransformation(),
                value = inputvalue.value,
                onValueChange = { inputvalue.value = it }
            )
        }
    }

    @Composable
    private fun SubmitButton(canValue: MutableState<TextFieldValue>, pinValue: MutableState<TextFieldValue>,
                             progressPrompt: MutableState<String>, progressText: MutableState<String>, progress: MutableState<Float>,
                             errorText: MutableState<String>
    ) {
        FilledButton("Submit", onClick = {
            can = canValue.value.text
            pin = pinValue.value.text

            GlobalScope.launch(Dispatchers.IO) {
                Log.i(TAG, "Connect card...")
                connectCard(progressPrompt, progressText, progress, errorText)

                val ohcc = OpenHealthCardCommunication(nfcCard)

                Log.i(TAG, "Initialize PACE...")
                ohcc.initializePACE(can, pin)
                publishProgress(PROGRESS_DONE, progressPrompt, progressText, progress, errorText)
            }
        },
            modifier = Modifier.padding(30.dp))
    }

    @Composable
    private fun ErrorText(errorText: MutableState<String>) {
        Text(
            text = errorText.value,
            fontFamily = FontFamily.SansSerif,
            fontSize = 18.sp,
            color = Color(android.graphics.Color.RED),
            modifier = Modifier.padding(horizontal = 50.dp)
        )
    }

    private fun connectCard(progressPrompt: MutableState<String>, progressText: MutableState<String>, progress: MutableState<Float>,
                            errorText: MutableState<String>) {
        while (true) {
            publishProgress(PROGRESS_WAIT_FOR_CARD, progressPrompt, progressText, progress, errorText)
            nfcCardTerminals!!.waitForChange()

            publishProgress(PROGRESS_CONNECT_TO_CARD, progressPrompt, progressText, progress, errorText)

            val list = nfcCardTerminals!!.list(CardTerminals.State.ALL)

            if (list.size == 1 && list[0] != null && list[0].isCardPresent) {
                nfcCard = list[0]!!.connect("T=CL")
                break
            } else {
                throw CardException("no card present")
            }
        }
    }

    private fun publishProgress(value: String, progressPrompt: MutableState<String>, progressText: MutableState<String>, progress: MutableState<Float>,
                                errorText: MutableState<String>) {
        publishProgress(value, null, progressPrompt, progressText, progress, errorText)
    }

    private fun publishProgress(value: String, optionalValue: String?,
                                progressPrompt: MutableState<String>, progressText: MutableState<String>, progress: MutableState<Float>,
                                errorText: MutableState<String>) {
        Log.d(TAG, "publishProgress: " + value)
        this.runOnUiThread {
            when (value) {
                PROGRESS_WAIT_FOR_CARD -> {
                    progressPrompt.value = "Bitte eGK ans Telefon halten!"
                    progress.value = 0.1f
                }

                PROGRESS_CONNECT_TO_CARD -> {
                    progressPrompt.value = "Bitte warten!"
                    progress.value = 0.5f
                }

                PROGRESS_DONE -> {
                    progressPrompt.value = "Bitte eGK entfernen!"
                    progress.value = 1.0f
                }

                PROGRESS_NO_CARD_PRESENT -> {
                    errorText.value = "Keine eGK gefunden!"
                }
            }
            progressText.value = value
        }
    }

}