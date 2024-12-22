package com.example.rotaty.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.example.rotaty.R
import com.example.rotaty.viewmodel.GameViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()
    private lateinit var congratsView: TextView
    private lateinit var restartButton: Button
    private lateinit var statsView: TextView
    private lateinit var targetTextView: TextView
    private lateinit var hiddenEditText: EditText
    private lateinit var timerTextView: TextView
    private var startTime: Long = 0
    private var totalCharacters: Int = 0
    private var correctCharacters: Int = 0
    private var currentPosition: Int = 0
    private var isRussianText: Boolean = true
    private var gameStateJob: Job? = null

    private fun isRussianLanguage(text: String): Boolean {
        val cyrillicRegex = Regex("[а-яА-ЯёЁ]")
        return text.contains(cyrillicRegex)
    }

    private fun calculateTypingSpeed(charactersTyped: Int, timeSpentMinutes: Double, isRussian: Boolean): Int {
        return if (isRussian) {
            (charactersTyped / timeSpentMinutes).toInt()
        } else {
            ((charactersTyped / 5.0) / timeSpentMinutes).toInt()
        }
    }

    private fun calculateAccuracy(correctChars: Int, totalTypedChars: Int, targetLength: Int): Int {
        if (totalTypedChars == 0) return 0

        val errors = totalTypedChars - correctChars
        val errorPercentage = (errors.toFloat() / targetLength.toFloat()) * 100
        return (100 - errorPercentage).coerceIn(0f, 100f).toInt()
    }

    private fun startGameStateCollection() {
        gameStateJob?.cancel()

        gameStateJob = lifecycleScope.launch {
            viewModel.gameState.collect { state ->
                val spannableString = SpannableString(state.targetText)
                val input = state.currentInput

                if (input.isEmpty()) {
                    isRussianText = isRussianLanguage(state.targetText)
                }

                if (currentPosition < state.targetText.length && !state.isSuccess) {
                    spannableString.setSpan(
                        BackgroundColorSpan(Color.parseColor("#E0E0E0")),
                        currentPosition, currentPosition + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                spannableString.forEachIndexed { index, _ ->
                    if (index < input.length) {
                        val isCorrect = input[index] == state.targetText[index]
                        val color = if (isCorrect) {
                            if (index >= correctCharacters) {
                                correctCharacters++
                            }
                            Color.GREEN
                        } else {
                            if (index == input.length - 1) {
                                val flashAnimation = AlphaAnimation(1f, 0.3f).apply {
                                    duration = 100
                                    repeatCount = 1
                                    repeatMode = AlphaAnimation.REVERSE
                                }
                                targetTextView.startAnimation(flashAnimation)
                            }
                            Color.RED
                        }

                        spannableString.setSpan(
                            ForegroundColorSpan(color),
                            index, index + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

                totalCharacters = input.length
                targetTextView.text = spannableString
                timerTextView.text = "Осталось времени: ${state.timeLeft} сек"

                if (state.isGameOver) {
                    val endTime = System.currentTimeMillis()
                    val timeSpentMinutes = (endTime - startTime) / 60000.0

                    val typingSpeed = calculateTypingSpeed(correctCharacters, timeSpentMinutes, isRussianText)
                    val accuracy = calculateAccuracy(correctCharacters, totalCharacters, state.targetText.length)

                    val speedUnit = if (isRussianText) "зн/мин" else "сл/мин"
                    statsView.apply {
                        text = "Скорость: $typingSpeed $speedUnit\nТочность: $accuracy%"
                        visibility = View.VISIBLE
                    }
                }

                congratsView.visibility = if (state.isSuccess) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = ConstraintLayout(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        val cardGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.WHITE,
                Color.WHITE
            )
        ).apply {
            cornerRadius = 24f
        }

        val container = MaterialCardView(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginStart = 32
                marginEnd = 32
            }
            radius = 24f
            elevation = 12f
            setCardBackgroundColor(Color.TRANSPARENT)
            background = cardGradient
        }

        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER
        }

        hiddenEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 1)
            isCursorVisible = false
            setBackgroundColor(Color.TRANSPARENT)
            isSingleLine = true
            addTextChangedListener { editable ->
                if (!viewModel.gameState.value.isSuccess) {
                    val text = editable?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        val lastChar = text.last()
                        val currentInput = viewModel.gameState.value.currentInput

                        if (currentPosition < viewModel.gameState.value.targetText.length) {
                            val newInput = StringBuilder(currentInput).apply {
                                if (currentPosition >= length) {
                                    append(lastChar)
                                } else {
                                    insert(currentPosition, lastChar)
                                }
                            }.toString()
                            currentPosition++
                            viewModel.onTextInput(newInput)
                        }
                        setText("")
                    }
                }
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DEL &&
                    currentPosition > 0
                ) {
                    currentPosition--
                    val currentInput = viewModel.gameState.value.currentInput
                    viewModel.onTextInput(currentInput.substring(0, currentPosition))
                    true
                } else {
                    false
                }
            }
        }

        timerTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 28f
            setTextColor(Color.parseColor("#2C3E50"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 24, 0, 24)
        }

        targetTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setOnClickListener {
                hiddenEditText.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        statsView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 18f
            setTextColor(Color.parseColor("#2C3E50"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            visibility = View.GONE
        }

        congratsView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 32f
            setTextColor(Color.parseColor("#27AE60"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            visibility = View.GONE
            text = "ПОЗДРАВЛЯЕМ!"
        }

        restartButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
                gravity = Gravity.CENTER
            }
            text = "Рестарт"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.BL_TR
                colors = intArrayOf(
                    Color.parseColor("#2980B9"),
                    Color.parseColor("#3498DB")
                )
                cornerRadius = 20f
            }
            elevation = 8f
            setPadding(48, 16, 48, 16)

            setOnClickListener {
                viewModel.startNewGame()
                currentPosition = 0
                congratsView.visibility = View.GONE
                statsView.visibility = View.GONE
                startTime = System.currentTimeMillis()
                totalCharacters = 0
                correctCharacters = 0
                startGameStateCollection()
                hiddenEditText.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        contentLayout.apply {
            addView(hiddenEditText)
            addView(targetTextView)
            addView(timerTextView)
            addView(statsView)
            addView(restartButton)
            addView(congratsView)
        }

        container.addView(contentLayout)
        rootLayout.addView(container)
        setContentView(rootLayout)

        startTime = System.currentTimeMillis()
        hiddenEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)

        startGameStateCollection()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameStateJob?.cancel()
    }
}