package com.rakib.voxmeet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

private val AuthBg = Color(0xFF071426)
private val AuthPanelColor = Color(0xFF10233D)
private val AuthPinkColor = Color(0xFF4DA3FF)
private val AuthVioletColor = Color(0xFF1769E0)
private val AuthMutedColor = Color(0xFFA9BCD2)
private val AuthErrorColor = Color(0xFFFF8A8A)

@Composable
fun VoxMeetAuthGate(content: @Composable () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val currentUser = user
    when {
        currentUser == null -> VoxMeetAuthScreen(auth)
        else -> content()
    }
}

@Composable
private fun VoxMeetAuthScreen(auth: FirebaseAuth) {
    var mode by remember { mutableStateOf("email") }
    var register by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("আপনার VoxMeet অ্যাকাউন্ট তৈরি করুন অথবা লগইন করুন") }
    var messageIsError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun showInfo(text: String) {
        message = text
        messageIsError = false
    }

    fun showError(text: String) {
        message = text
        messageIsError = true
    }

    fun requestPhoneCode(forceResend: Boolean) {
        val normalizedPhone = phone.trim().replace(" ", "").replace("-", "")
        val activity = context.findHostActivity()
        if (activity == null) {
            showError("ফোন যাচাই শুরু করা যায়নি। অ্যাপটি আবার খুলে চেষ্টা করুন।")
            return
        }
        if (!Regex("^\\+[1-9]\\d{7,14}$").matches(normalizedPhone)) {
            showError("দেশের কোডসহ সঠিক ফোন নম্বর লিখুন, যেমন +8801XXXXXXXXX")
            return
        }

        busy = true
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnCompleteListener { result ->
                    busy = false
                    if (result.isSuccessful) showInfo("ফোন যাচাই সফল হয়েছে。")
                    else showError(authErrorMessage(result.exception, "ফোন দিয়ে লগইন করা যায়নি।"))
                }
            }

            override fun onVerificationFailed(error: com.google.firebase.FirebaseException) {
                busy = false
                showError(authErrorMessage(error, "OTP পাঠানো যায়নি। Firebase Phone provider ও নম্বর পরীক্ষা করুন।"))
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                busy = false
                showInfo("OTP পাঠানো হয়েছে। SMS থেকে ৬ সংখ্যার কোড লিখুন।")
            }

            override fun onCodeAutoRetrievalTimeOut(id: String) {
                verificationId = id
                busy = false
                showInfo("স্বয়ংক্রিয় যাচাইয়ের সময় শেষ। SMS-এর OTP লিখুন অথবা আবার পাঠান।")
            }
        }

        try {
            val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(normalizedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            if (forceResend) {
                resendToken?.let { optionsBuilder.setForceResendingToken(it) }
            }
            PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
        } catch (error: Exception) {
            busy = false
            showError(authErrorMessage(error, "OTP অনুরোধ করা যায়নি। আবার চেষ্টা করুন।"))
        }
    }

    Surface(Modifier.fillMaxSize(), color = AuthBg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("VOXMEET", color = AuthPinkColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            Text("Connect, share and shine", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("নিরাপদে অ্যাকাউন্টে প্রবেশ করুন", color = AuthMutedColor, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().background(AuthPanelColor, RoundedCornerShape(14.dp)).padding(4.dp)) {
                listOf("email" to "ইমেইল", "phone" to "ফোন OTP").forEach { (key, label) ->
                    TextButton(
                        onClick = {
                            mode = key
                            verificationId = null
                            resendToken = null
                            otp = ""
                            showInfo(if (key == "email") "ইমেইল দিয়ে প্রবেশ করুন" else "দেশের কোডসহ ফোন নম্বর দিন")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = if (mode == key) Color.White else AuthMutedColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (mode == "email") {
                if (register) {
                    AuthInput(displayName, { displayName = it }, "আপনার নাম")
                    Spacer(Modifier.height(10.dp))
                }
                AuthInput(email, { email = it }, "ইমেইল ঠিকানা", KeyboardType.Email)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("পাসওয়ার্ড") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    colors = authInputColors()
                )
                if (register) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("পাসওয়ার্ড নিশ্চিত করুন") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        colors = authInputColors()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val cleanEmail = email.trim()
                        if (register && displayName.trim().length < 2) {
                            showError("আপনার নাম কমপক্ষে ২ অক্ষরের লিখুন।")
                        } else if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                            showError("সঠিক ইমেইল ঠিকানা লিখুন।")
                        } else if (password.length < 6) {
                            showError("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।")
                        } else if (register && password != confirmPassword) {
                            showError("দুটি পাসওয়ার্ড মিলছে না।")
                        } else {
                            busy = true
                            val task = if (register) auth.createUserWithEmailAndPassword(cleanEmail, password)
                                else auth.signInWithEmailAndPassword(cleanEmail, password)
                            task.addOnCompleteListener { result ->
                                if (!result.isSuccessful) {
                                    busy = false
                                    showError(authErrorMessage(result.exception, "লগইন বা রেজিস্ট্রেশন সম্পন্ন হয়নি।"))
                                } else {
                                    val current = auth.currentUser
                                    if (register && current != null) {
                                        val profile = UserProfileChangeRequest.Builder()
                                            .setDisplayName(displayName.trim())
                                            .build()
                                        current.updateProfile(profile).addOnCompleteListener { profileResult ->
                                            if (!profileResult.isSuccessful) {
                                                busy = false
                                                showError(authErrorMessage(profileResult.exception, "প্রোফাইলের নাম সংরক্ষণ করা যায়নি।"))
                                            } else {
                                                busy = false
                                                showInfo("রেজিস্ট্রেশন সফল! আপনার VoxMeet অ্যাকাউন্ট তৈরি হয়েছে। এখনই ব্যবহার শুরু করতে পারবেন।")
                                            }
                                        }
                                    } else {
                                        busy = false
                                        showInfo("লগইন সফল।")
                                    }
                                }
                            }
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuthVioletColor)
                ) { Text(if (busy) "অপেক্ষা করুন..." else if (register) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") }
                TextButton(onClick = {
                    register = !register
                    showInfo(if (register) "নতুন অ্যাকাউন্ট তৈরি করুন" else "আপনার অ্যাকাউন্টে লগইন করুন")
                }, enabled = !busy) {
                    Text(if (register) "আগে অ্যাকাউন্ট আছে? লগইন করুন" else "নতুন ব্যবহারকারী? রেজিস্ট্রেশন করুন", color = AuthPinkColor)
                }
                TextButton(onClick = {
                    val cleanEmail = email.trim()
                    if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                        showError("পাসওয়ার্ড রিসেটের আগে সঠিক ইমেইল লিখুন।")
                    } else {
                        busy = true
                        auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener { result ->
                            busy = false
                            if (result.isSuccessful) {
                                showInfo("যদি এই ইমেইলে অ্যাকাউন্ট থাকে, পাসওয়ার্ড রিসেটের নির্দেশনা পাঠানো হয়েছে।")
                            } else {
                                showError(authErrorMessage(result.exception, "রিসেট ইমেইল পাঠানো যায়নি।"))
                            }
                        }
                    }
                }, enabled = !busy) { Text("পাসওয়ার্ড ভুলে গেছেন?", color = AuthMutedColor) }
            } else {
                AuthInput(phone, { phone = it }, "ফোন নম্বর (+880...)", KeyboardType.Phone)
                Spacer(Modifier.height(10.dp))
                if (verificationId == null) {
                    Button(
                        onClick = { requestPhoneCode(forceResend = false) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AuthVioletColor)
                    ) {
                        Text(if (busy) "OTP পাঠানো হচ্ছে..." else "OTP পাঠান")
                    }
                } else {
                    AuthInput(
                        otp,
                        { otp = it.filter(Char::isDigit).take(6) },
                        "SMS-এর ৬ সংখ্যার OTP",
                        KeyboardType.NumberPassword
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val id = verificationId
                            if (id.isNullOrBlank() || otp.length != 6) {
                                showError("SMS থেকে পাওয়া ৬ সংখ্যার OTP লিখুন।")
                            } else {
                                busy = true
                                auth.signInWithCredential(PhoneAuthProvider.getCredential(id, otp)).addOnCompleteListener { result ->
                                    busy = false
                                    if (result.isSuccessful) {
                                        showInfo("ফোন লগইন সফল।")
                                    } else {
                                        showError(authErrorMessage(result.exception, "OTP সঠিক নয় বা মেয়াদ শেষ হয়েছে।"))
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AuthVioletColor)
                    ) {
                        Text(if (busy) "যাচাই হচ্ছে..." else "OTP যাচাই করুন")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { requestPhoneCode(forceResend = true) },
                            enabled = !busy && resendToken != null
                        ) {
                            Text("OTP আবার পাঠান", color = AuthPinkColor)
                        }
                        TextButton(
                            onClick = {
                                verificationId = null
                                resendToken = null
                                otp = ""
                                showInfo("নতুন ফোন নম্বর লিখুন।")
                            },
                            enabled = !busy
                        ) {
                            Text("নম্বর পরিবর্তন", color = AuthMutedColor)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = if (messageIsError) AuthErrorColor else AuthMutedColor, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("আপনার তথ্য Firebase Authentication দিয়ে যাচাই করা হবে।", color = AuthMutedColor, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmailVerificationScreen(
    auth: FirebaseAuth,
    user: FirebaseUser,
    onRefresh: (FirebaseUser) -> Unit
) {
    var message by remember(user.uid) { mutableStateOf("আপনার ইমেইলে পাঠানো লিংকে চাপ দিয়ে অ্যাকাউন্ট ভেরিফাই করুন।") }
    var messageIsError by remember(user.uid) { mutableStateOf(false) }
    var busy by remember(user.uid) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = AuthBg) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ইমেইল যাচাই করুন", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(user.email ?: "", color = AuthPinkColor)
            Spacer(Modifier.height(16.dp))
            Text(message, color = if (messageIsError) AuthErrorColor else AuthMutedColor)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                busy = true
                user.reload().addOnCompleteListener { result ->
                    busy = false
                    if (result.isSuccessful) {
                        val refreshedUser = auth.currentUser ?: user
                        onRefresh(refreshedUser)
                        messageIsError = false
                        message = if (refreshedUser.isEmailVerified) "ইমেইল ভেরিফাই হয়েছে।" else "এখনো ভেরিফাই হয়নি। ইমেইলের লিংকে চাপ দিন।"
                    } else {
                        messageIsError = true
                        message = authErrorMessage(result.exception, "ভেরিফিকেশন অবস্থা যাচাই করা যায়নি।")
                    }
                }
            }, enabled = !busy) { Text("ভেরিফিকেশন চেক করুন") }
            TextButton(onClick = {
                busy = true
                user.sendEmailVerification().addOnCompleteListener { result ->
                    busy = false
                    messageIsError = !result.isSuccessful
                    message = if (result.isSuccessful) "আবার ভেরিফিকেশন ইমেইল পাঠানো হয়েছে।"
                        else authErrorMessage(result.exception, "ইমেইল পাঠানো যায়নি।")
                }
            }, enabled = !busy) { Text("আবার ইমেইল পাঠান", color = AuthPinkColor) }
            TextButton(onClick = { auth.signOut() }, enabled = !busy) { Text("অন্য অ্যাকাউন্ট দিয়ে প্রবেশ করুন", color = AuthMutedColor) }
        }
    }
}

@Composable
private fun AuthInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = authInputColors()
    )
}

@Composable
private fun authInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = AuthVioletColor, unfocusedBorderColor = AuthPanelColor,
    focusedLabelColor = AuthPinkColor, unfocusedLabelColor = AuthMutedColor,
    cursorColor = AuthPinkColor, focusedContainerColor = AuthPanelColor, unfocusedContainerColor = AuthPanelColor
)


private fun authErrorMessage(error: Throwable?, fallback: String): String {
    val code = (error as? FirebaseAuthException)?.errorCode
    return when (code) {
        "ERROR_INVALID_EMAIL" -> "ইমেইল ঠিকানাটি সঠিক নয়।"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "এই ইমেইল দিয়ে আগে থেকেই অ্যাকাউন্ট আছে। লগইন করুন।"
        "ERROR_WEAK_PASSWORD" -> "আরও শক্তিশালী পাসওয়ার্ড দিন। কমপক্ষে ৬ অক্ষর প্রয়োজন।"
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "ইমেইল বা পাসওয়ার্ড সঠিক নয়।"
        "ERROR_USER_DISABLED" -> "এই অ্যাকাউন্টটি সাময়িকভাবে বন্ধ আছে।"
        "ERROR_OPERATION_NOT_ALLOWED" -> "Firebase Console-এ প্রয়োজনীয় Email/Phone sign-in provider চালু করতে হবে।"
        "ERROR_INVALID_PHONE_NUMBER" -> "দেশের কোডসহ সঠিক ফোন নম্বর লিখুন।"
        "ERROR_INVALID_VERIFICATION_CODE" -> "OTP কোডটি সঠিক নয়। আবার চেষ্টা করুন।"
        "ERROR_SESSION_EXPIRED" -> "OTP-এর মেয়াদ শেষ হয়েছে। নতুন OTP নিন।"
        "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_APP_CREDENTIAL" -> "Firebase Console-এ Android SHA-1/SHA-256 fingerprint ও Phone Auth সেটিংস যাচাই করতে হবে।"
        "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
        "ERROR_NETWORK_REQUEST_FAILED" -> "ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
        else -> error?.localizedMessage ?: fallback
    }
}

private fun Context.findHostActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
