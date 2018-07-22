package com.androingenio.askimposibles

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.androingenio.askimposibles.utils.Base
import com.androingenio.askimposibles.utils.Constantes
import com.androingenio.askimposibles.utils.models.User
import com.facebook.*
import com.facebook.appevents.AppEventsLogger
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_splash.*
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class SplashActivity : AppCompatActivity() {
    internal lateinit var database: FirebaseDatabase
    internal lateinit var myRef: DatabaseReference
    internal lateinit var pref: SharedPreferences
    private var mAuth: FirebaseAuth? = null
    private var mCallbackManager: CallbackManager? = null
    internal var foto: String = ""
    internal var nombre:String = ""
    internal var link:String = ""
    internal var id:String = ""
    internal var email:String= ""
    internal var birthday:String = ""
    internal var age:String = ""
    internal var gender:String = ""
    val TAG = "FacebookLogin"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_splash)
        FacebookSdk.sdkInitialize(applicationContext)
        AppEventsLogger.activateApp(this)
        pref = getSharedPreferences(Constantes.NAME_PREF, Context.MODE_PRIVATE)
        database = FirebaseDatabase.getInstance()
        mAuth = FirebaseAuth.getInstance()
        myRef = database.reference
        mCallbackManager = CallbackManager.Factory.create()
        login_button.setReadPermissions("email", "public_profile")
        login_button.registerCallback(mCallbackManager,object : FacebookCallback<LoginResult>{
            /**
             * Called when the dialog completes without error.
             *
             *
             * Note: This will be called instead of [.onCancel] if any of the following conditions
             * are true.
             *
             *  *
             * [com.facebook.share.widget.MessageDialog] is used.
             *
             *  *
             * The logged in Facebook user has not authorized the app that has initiated the dialog.
             *
             *
             *
             * @param result Result from the dialog
             */
            override fun onSuccess(result: LoginResult?) {
                Log.d(TAG, "facebook:onSuccess:" + result!!.getAccessToken().getUserId())
                if (Profile.getCurrentProfile() != null) {
                    val profile = Profile.getCurrentProfile()
                    foto = profile.getProfilePictureUri(500, 500).toString()
                    link = profile.linkUri.toString()
                    nombre = profile.name
                    id = profile.id
                } else {
                    val profileTracker = object : ProfileTracker() {
                        override fun onCurrentProfileChanged(oldProfile: Profile, currentProfile: Profile) {
                            stopTracking()
                            Profile.setCurrentProfile(currentProfile)
                            val profile = Profile.getCurrentProfile()
                            foto = profile.getProfilePictureUri(500, 500).toString()
                            link = profile.linkUri.toString()
                            nombre = profile.name
                            id = profile.id
                        }
                    }
                    profileTracker.startTracking()
                }
                val request = GraphRequest.newMeRequest(result.getAccessToken()) { `object`, response ->
                    try {
                        Log.d("object FAcebook", `object`.toString())
                        if (`object`.has("email")) {

                            email = `object`.getString("email")
                        }

                        if (`object`.has("birthday")) {
                            birthday = `object`.getString("birthday") // 01/31/1980 format
                        }

                        if(`object`.has("gender")){
                            gender = `object`.getString("gender")
                        }

                        if(`object`.has("age_range")){
                            age = `object`.getJSONObject("age_range").getString("min")
                        }






                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }




                val parameters = Bundle()
                parameters.putString("fields", "email,gender,birthday,age_range")
                request.parameters = parameters
                request.executeAsync()

                handleFacebookAccessToken(result.getAccessToken())

            }

            /**
             * Called when the dialog is canceled.
             *
             *
             * Note: [.onSuccess] will be called instead if any of the following conditions
             * are true.
             *
             *  *
             * [com.facebook.share.widget.MessageDialog] is used.
             *
             *  *
             * The logged in Facebook user has not authorized the app that has initiated the dialog.
             *
             *
             */
            override fun onCancel() {
                Log.d(TAG, "facebook:onCancel")
                updateUI(null)
            }

            /**
             * Called when the dialog finishes with an error.
             *
             * @param error The error that occurred
             */
            override fun onError(error: FacebookException?) {
                Log.e(TAG, "facebook:onError",error)
                // [START_EXCLUDE]
                updateUI(null)
            }

        })
    }

    override fun onStart() {
        super.onStart()

        val currentUser = mAuth!!.getCurrentUser()
        updateUI(currentUser)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Pass the activity result back to the Facebook SDK
        mCallbackManager!!.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        Log.d(TAG, "handleFacebookAccessToken:$token")


        val credential = FacebookAuthProvider.getCredential(token.token)
        mAuth!!.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithCredential:success")
                        val user = mAuth!!.currentUser!!


                        //val mUser = User(user.uid, nombre!!, foto!!, link!!, id!!, email!!, birthday!!, age!!, gender!!)

                        val mUser = User()
                        mUser.uId = user.uid
                        mUser.nombre = nombre

                        Log.d(TAG, mUser.toString())
                        Base.getInstance(this@SplashActivity).saveUser(user.uid, nombre, foto, link, id, email, birthday, age, gender)

                        Log.d(TAG, user.uid)
                        FirebaseDatabase.getInstance().getReference("Usuarios").child(user.uid).setValue(mUser).addOnFailureListener {
                            Log.d(TAG,"error",it)
                            signOut()
                        }.addOnSuccessListener {
                            updateUI(user)

                            val params = Bundle()
                            params.putString("idUser", user.uid)
                            params.putString("nombreUser", nombre)
                            App.getmFirebaseAnalytics().logEvent("UsuarioRegistradp", params)
                            App.getmFirebaseAnalytics().setUserId(user.uid)
                            App.getmFirebaseAnalytics().setUserProperty("Genero", gender)
                            App.getmFirebaseAnalytics().setUserProperty("Edad", age)
                        }

                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInWithCredential:failure", task.exception)
                        Toast.makeText(this@SplashActivity, "Authentication failed.",
                                Toast.LENGTH_SHORT).show()
                        updateUI(null)
                    }
                }
    }
    // [END auth_with_facebook]

    fun signOut() {
        mAuth!!.signOut()
        LoginManager.getInstance().logOut()

        updateUI(null)
    }

    private fun updateUI(user: FirebaseUser?) {

        if (user != null) {


            login_button.setVisibility(View.GONE)


            val timerTask = object : TimerTask() {
                override fun run() {

                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }

            val timer = Timer()
            timer.schedule(timerTask, 3000)


        } else {


            login_button.setVisibility(View.VISIBLE)


        }
    }

}
