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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import de.gematik.refpopp.popp_module.ui.theme.PoppModuleAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoppModuleAppTheme {
                StartDialog(this@MainActivity)
            }
        }
    }
}

@Composable
fun StartDialog(activity: Activity) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header()

        Text(
            text = "Möchten Sie sich mit Gesundheits-ID oder eGK authentifizieren?",
            fontFamily = FontFamily.SansSerif,
            fontSize = 20.sp,
            modifier = Modifier.padding(30.dp)
        )

        FilledButton(
            "Weiter mit Gesundheits-ID", onClick = {
                    Toast.makeText(
                        activity,
                        "Sorry, noch nicht implementiert.",
                        Toast.LENGTH_LONG
                    ).show()
            },
            modifier = Modifier.padding(30.dp)
        )

        FilledButton(
            "Weiter mit eGK", onClick = {
                val intent = Intent(activity, EgkAuthActivity::class.java)
                startActivity(activity, intent, null)
                activity.finish()
            },
            modifier = Modifier.padding(30.dp)
        )
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
            text = "PoPP Modul App",
            fontFamily = FontFamily.SansSerif,
            fontSize = 30.sp,
            modifier = Modifier.padding(20.dp)
        )
    }

}

@Composable
fun FilledButton(label: String, onClick: () -> Unit = ({ }), modifier: Modifier, fontSize: TextUnit = 20.sp) {
    Button(
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(),
        onClick = { onClick() })
    {
        Text(
            text = label,
            color = Color.Black,
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal
        )
    }
}

