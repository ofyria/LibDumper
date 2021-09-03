package com.libdumper

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.ScrollView
import com.libdumper.Utils.TAG
import com.libdumper.databinding.ActivityMainBinding
import com.libdumper.dumper.Dumper
import com.libdumper.root.RootServices
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService

class MainActivity : Activity(), Handler.Callback {
    lateinit var bind: ActivityMainBinding
    private val myMessenger = Messenger(Handler(Looper.getMainLooper(), this))
    var remoteMessenger: Messenger? = null
    private var serviceTestQueued = false
    private var conn: MSGConnection? = null
    private var nativeDir = ""
    private var needFix: Boolean = false
    private fun initRoot() {
        if (Shell.rootAccess()) {
            if (remoteMessenger == null) {
                serviceTestQueued = true
                val intent = Intent(this, RootServices::class.java)
                conn = MSGConnection()
                RootService.bind(intent, conn!!)
                return
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        initRoot()
        nativeDir = applicationInfo.nativeLibraryDir

        with(bind) {
            setContentView(root)
            beginDump.setOnClickListener {
                if (pkg.text != null) {
                    if (metadata.isChecked)
                        runNative("global-metadata.dat")

                    needFix = autoFix.isChecked
                    runNative(
                        if (libName.text.isNullOrBlank())
                            "libil2cpp.so"
                        else
                            libName.text.toString()
                    )
                } else {
                    consoleList.add("put pkg name!")
                }
            }
            github.setOnClickListener {
                startActivity(
                    Intent(
                        ACTION_VIEW,
                        Uri.parse("https://github.com/BryanGIG/LibDumper")
                    )
                )
            }
        }
    }


    override fun handleMessage(msg: Message): Boolean {
        val dump = msg.data.getString("result")
        consoleList.add(dump)
        return false
    }

    private fun runNative(file: String) {
        val pkg = bind.pkg.text.toString()
        if (Shell.rootAccess()) {
            sendRequest(pkg, file)
        } else {
            consoleList.add(Dumper(nativeDir,pkg, file).dumpFile(needFix))
        }
    }

    private fun sendRequest(pkg: String, file: String) {
        val message: Message = Message.obtain(null, Utils.MSG_GETINFO)
        message.data.putBoolean("fixMe", needFix)
        message.data.putString("native",nativeDir)
        message.data.putString("pkg", pkg)
        message.data.putString("file_dump", file)
        message.replyTo = myMessenger
        try {
            remoteMessenger?.send(message)
        } catch (e: RemoteException) {
            Log.e(TAG, "Remote error", e)
        }
    }

    inner class MSGConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "service onServiceConnected")
            remoteMessenger = Messenger(service)
            if (serviceTestQueued) {
                serviceTestQueued = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "service onServiceDisconnected")
            remoteMessenger = null
        }
    }

    private var consoleList = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            bind.console.append(s)
            bind.console.append("\n")
            bind.sv.postDelayed({ bind.sv.fullScroll(ScrollView.FOCUS_DOWN) }, 10)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        conn?.let {
            RootService.unbind(it)
        }
    }
}
