package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.TealAccent
import java.text.DecimalFormat

@Composable
fun CalculatorScreen(
    secretCode: String,
    onSecretCodeMatched: () -> Unit,
    onCloseCalculator: () -> Unit
) {
    var displayExpression by remember { mutableStateOf("0") }
    var currentResult by remember { mutableStateOf("") }
    var lastOperator by remember { mutableStateOf<Char?>(null) }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var resetInputOnNextDigit by remember { mutableStateOf(false) }

    fun checkSecretMatch(inputVal: String) {
        val cleanSecret = secretCode.trim()
        if (cleanSecret.isNotEmpty() && inputVal.trim() == cleanSecret) {
            onSecretCodeMatched()
        }
    }

    fun onDigitPress(digit: String) {
        if (displayExpression == "0" || resetInputOnNextDigit) {
            displayExpression = digit
            resetInputOnNextDigit = false
        } else {
            if (displayExpression.length < 15) {
                displayExpression += digit
            }
        }
        checkSecretMatch(displayExpression)
    }

    fun onDecimalPress() {
        if (resetInputOnNextDigit) {
            displayExpression = "0."
            resetInputOnNextDigit = false
        } else if (!displayExpression.contains(".")) {
            displayExpression += "."
        }
        checkSecretMatch(displayExpression)
    }

    fun onOperatorPress(op: Char) {
        val currentVal = displayExpression.toDoubleOrNull() ?: 0.0
        firstOperand = currentVal
        lastOperator = op
        resetInputOnNextDigit = true
    }

    fun calculateResult() {
        // Also check if user typed secret code and pressed '='
        checkSecretMatch(displayExpression)

        val first = firstOperand
        val op = lastOperator
        val second = displayExpression.toDoubleOrNull()

        if (first != null && op != null && second != null) {
            val res = when (op) {
                '+' -> first + second
                '-' -> first - second
                '×', '*' -> first * second
                '÷', '/' -> if (second != 0.0) first / second else Double.NaN
                '%' -> first * (second / 100.0)
                else -> second
            }

            if (res.isNaN() || res.isInfinite()) {
                displayExpression = "Error"
            } else {
                val formatted = if (res % 1.0 == 0.0) {
                    res.toLong().toString()
                } else {
                    DecimalFormat("#.########").format(res)
                }
                displayExpression = formatted
                currentResult = formatted
                checkSecretMatch(formatted)
            }
            firstOperand = null
            lastOperator = null
            resetInputOnNextDigit = true
        }
    }

    fun clearAll() {
        displayExpression = "0"
        currentResult = ""
        firstOperand = null
        lastOperator = null
        resetInputOnNextDigit = false
    }

    fun backspace() {
        if (displayExpression.length > 1) {
            displayExpression = displayExpression.dropLast(1)
        } else {
            displayExpression = "0"
        }
        checkSecretMatch(displayExpression)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar with Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "yt",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = onCloseCalculator,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
                    .testTag("btn_close_calculator")
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Calculator",
                    tint = Color.White
                )
            }
        }

        // Display Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            if (firstOperand != null && lastOperator != null) {
                Text(
                    text = "${DecimalFormat("#.######").format(firstOperand)} $lastOperator",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Text(
                text = displayExpression,
                fontSize = if (displayExpression.length > 9) 36.sp else 48.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag("calc_display")
            )
        }

        // Keypad Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: AC, Backspace, %, ÷
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcButton(
                    text = "AC",
                    textColor = ShortsRed,
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { clearAll() }
                )
                CalcIconButton(
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { backspace() }
                )
                CalcButton(
                    text = "%",
                    textColor = TealAccent,
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { onOperatorPress('%') }
                )
                CalcButton(
                    text = "÷",
                    textColor = TealAccent,
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { onOperatorPress('÷') }
                )
            }

            // Row 2: 7, 8, 9, ×
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcButton(text = "7", modifier = Modifier.weight(1f), onClick = { onDigitPress("7") })
                CalcButton(text = "8", modifier = Modifier.weight(1f), onClick = { onDigitPress("8") })
                CalcButton(text = "9", modifier = Modifier.weight(1f), onClick = { onDigitPress("9") })
                CalcButton(
                    text = "×",
                    textColor = TealAccent,
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { onOperatorPress('×') }
                )
            }

            // Row 3: 4, 5, 6, -
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcButton(text = "4", modifier = Modifier.weight(1f), onClick = { onDigitPress("4") })
                CalcButton(text = "5", modifier = Modifier.weight(1f), onClick = { onDigitPress("5") })
                CalcButton(text = "6", modifier = Modifier.weight(1f), onClick = { onDigitPress("6") })
                CalcButton(
                    text = "-",
                    textColor = TealAccent,
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { onOperatorPress('-') }
                )
            }

            // Row 4: 1, 2, 3, +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcButton(text = "1", modifier = Modifier.weight(1f), onClick = { onDigitPress("1") })
                CalcButton(text = "2", modifier = Modifier.weight(1f), onClick = { onDigitPress("2") })
                CalcButton(text = "3", modifier = Modifier.weight(1f), onClick = { onDigitPress("3") })
                CalcButton(
                    text = "+",
                    textColor = TealAccent,
                    backgroundColor = DarkSurfaceElevated,
                    modifier = Modifier.weight(1f),
                    onClick = { onOperatorPress('+') }
                )
            }

            // Row 5: 00, 0, ., =
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcButton(text = "00", modifier = Modifier.weight(1f), onClick = { onDigitPress("00") })
                CalcButton(text = "0", modifier = Modifier.weight(1f), onClick = { onDigitPress("0") })
                CalcButton(text = ".", modifier = Modifier.weight(1f), onClick = { onDecimalPress() })
                CalcButton(
                    text = "=",
                    textColor = Color.White,
                    backgroundColor = TealAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { calculateResult() }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CalcButton(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    backgroundColor: Color = DarkSurface,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1.15f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

@Composable
private fun CalcIconButton(
    modifier: Modifier = Modifier,
    backgroundColor: Color = DarkSurface,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1.15f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Backspace",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
