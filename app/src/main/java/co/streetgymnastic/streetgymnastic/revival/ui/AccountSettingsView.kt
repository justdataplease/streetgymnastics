package co.streetgymnastic.streetgymnastic.revival.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import co.streetgymnastic.streetgymnastic.revival.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/** Firebase owns session persistence; passwords never enter app preferences or saved view state. */
class AccountSettingsView(private val activity: Activity) : LinearLayout(activity) {
    private val auth = FirebaseAuth.getInstance()
    private var creatingAccount = false
    private var busy = false
    private val listener = FirebaseAuth.AuthStateListener { render() }

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        background = roundedDrawable(AppColors.SURFACE, context.dp(10).toFloat())
        elevation = context.dp(2).toFloat()
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        auth.addAuthStateListener(listener)
    }

    override fun onDetachedFromWindow() {
        auth.removeAuthStateListener(listener)
        super.onDetachedFromWindow()
    }

    private fun render() {
        removeAllViews()
        val user = auth.currentUser
        addView(context.titleText(context.getString(if (user == null) R.string.account_title else R.string.account_signed_in), 20f))
        if (user != null) {
            addWithMargins(context.bodyText(user.email.orEmpty()), topDp = 8)
            addWithMargins(context.bodyText(context.getString(R.string.account_local_progress), 13f), topDp = 8)
            addWithMargins(context.secondaryButton(context.getString(R.string.account_sign_out)) {
                creatingAccount = false
                auth.signOut()
            }, topDp = 12)
            return
        }
        addWithMargins(context.bodyText(context.getString(R.string.account_optional), 14f), topDp = 6)
        val email = field(R.string.account_email, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field(R.string.account_password, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirmation = field(R.string.account_confirm_password, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        confirmation.visibility = if (creatingAccount) View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
            password.setAutofillHints(if (creatingAccount) "newPassword" else View.AUTOFILL_HINT_PASSWORD)
            confirmation.setAutofillHints("newPassword")
        }
        addWithMargins(email, topDp = 12)
        addView(password)
        addView(confirmation)
        val status = context.bodyText("", 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addWithMargins(status, topDp = 8)
        val controls = mutableListOf<View>(email, password, confirmation)
        fun setBusy(value: Boolean) {
            busy = value
            controls.forEach { it.isEnabled = !value }
            if (value) status.setText(R.string.account_working)
        }
        fun validEmail(): String? {
            val value = email.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                email.error = context.getString(R.string.account_invalid_email)
                email.requestFocus()
                return null
            }
            return value
        }
        val submit = context.primaryButton(context.getString(if (creatingAccount) R.string.account_create else R.string.account_sign_in)) {
            if (!busy) {
                val address = validEmail()
                val secret = password.text.toString()
                when {
                    address == null -> Unit
                    secret.isEmpty() -> { password.error = context.getString(R.string.account_enter_password); password.requestFocus() }
                    creatingAccount && secret.length < 6 -> { password.error = context.getString(R.string.account_short_password); password.requestFocus() }
                    creatingAccount && secret != confirmation.text.toString() -> {
                        confirmation.error = context.getString(R.string.account_password_mismatch)
                        confirmation.requestFocus()
                    }
                    else -> {
                        setBusy(true)
                        val task = if (creatingAccount) auth.createUserWithEmailAndPassword(address, secret)
                            else auth.signInWithEmailAndPassword(address, secret)
                        task.addOnCompleteListener(activity) { result ->
                            password.text.clear()
                            confirmation.text.clear()
                            if (isAttachedToWindow) {
                                setBusy(false)
                                if (result.isSuccessful) render() else status.text = errorMessage(result.exception)
                            }
                        }
                    }
                }
            }
        }
        controls.add(submit)
        addWithMargins(submit, topDp = 8)
        val mode = context.secondaryButton(context.getString(if (creatingAccount) R.string.account_have_account else R.string.account_new_account)) {
            creatingAccount = !creatingAccount
            render()
        }
        controls.add(mode)
        addView(mode)
        val reset = context.secondaryButton(context.getString(R.string.account_reset)) {
            val address = validEmail()
            if (address != null && !busy) {
                setBusy(true)
                auth.sendPasswordResetEmail(address).addOnCompleteListener(activity) { result ->
                    if (isAttachedToWindow) {
                        setBusy(false)
                        status.text = if (result.isSuccessful) context.getString(R.string.account_reset_sent) else errorMessage(result.exception)
                    }
                }
            }
        }
        controls.add(reset)
        addView(reset)
        setBusy(busy)
    }

    private fun field(label: Int, type: Int) = EditText(context).apply {
        hint = context.getString(label)
        contentDescription = hint
        inputType = type
        isSingleLine = true
        minHeight = context.dp(56)
        textSize = 16f
        setTextColor(AppColors.TEXT)
        setHintTextColor(AppColors.TEXT_SECONDARY)
        typeface = Typeface.DEFAULT
        isSaveEnabled = false
    }

    private fun errorMessage(error: Exception?): String = context.getString(when {
        error is FirebaseNetworkException -> R.string.account_network_error
        error is FirebaseTooManyRequestsException -> R.string.account_rate_limit
        error is FirebaseAuthWeakPasswordException -> R.string.account_weak_password
        error is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.account_email_in_use
            "ERROR_OPERATION_NOT_ALLOWED" -> R.string.account_unavailable
            "ERROR_USER_DISABLED" -> R.string.account_disabled
            "ERROR_INVALID_EMAIL" -> R.string.account_invalid_email
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> R.string.account_invalid_credentials
            else -> R.string.account_generic_error
        }
        else -> R.string.account_generic_error
    })
}
