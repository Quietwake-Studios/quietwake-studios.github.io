package com.tavoohoh.quietwords.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tavoohoh.quiet.ui.QuietTheme
import com.tavoohoh.quiet.ui.QuietType
import com.tavoohoh.quiet.ui.quietClickable
import com.tavoohoh.quietwords.R
import com.tavoohoh.quietwords.core.WordText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** "Guess the hidden word": slots, the keyboard, 3 tries; a miss costs nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuessSheet(model: GameModel, onDismiss: () -> Unit) {
    val colors = QuietTheme.colors
    val reduceMotion = QuietTheme.reduceMotion
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val session = model.state
    val length = session.board.hidden.length
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }
    val fieldLabel = stringResource(R.string.hidden_word)

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun submit(guess: String) {
        if (model.guess(guess)) {
            close()
        } else {
            wrong = true
            scope.launch {
                delay(450)
                text = ""
                wrong = false
                if (!model.state.canGuess) close()
            }
        }
    }

    LaunchedEffect(model.guessShake) {
        if (model.guessShake > 0 && !reduceMotion) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(240, easing = LinearEasing))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.guess_title), style = QuietType.title3, color = colors.ink)
            Text(
                stringResource(R.string.guess_explain, session.scoring.hiddenGuessed), style = QuietType.subheadline,
                color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
            )
            HiddenSlots(
                length = length,
                letters = WordText.normalize(text, session.language).take(length),
                state = if (wrong) SlotState.WRONG else SlotState.TYPING,
                size = if (length > 7) 34.dp else 42.dp,
                modifier = Modifier
                    .offset { IntOffset((4.dp.toPx() * sin(shake.value * PI * 6)).roundToInt(), 0) }
                    .quietClickable {
                        focus.requestFocus()
                        keyboard?.show()
                    },
            )
            Text(pluralStringResource(R.plurals.tries_left, session.guessesLeft, session.guessesLeft), style = QuietType.footnote, color = colors.muted)
            BasicTextField(
                value = text,
                onValueChange = { value ->
                    wrong = false
                    val clipped = WordText.normalize(value, session.language).take(length)
                    text = clipped
                    if (clipped.length == length) submit(clipped)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Text,
                ),
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.01f)
                    .focusRequester(focus)
                    .semantics { contentDescription = fieldLabel }
                    .testTag("guessField"),
            )
        }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}
