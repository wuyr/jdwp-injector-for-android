### 简介：
借助Android 11以上自带的无线adb，给普通手机(无root)提供一个对debuggable app进行动态代码注入的能力，无需依赖额外工具。

<br/>

### 博客详情： 敬请期待。。。

<br/>

### 姊妹篇：[agent-injector-for-android](https://github.com/wuyr/agent-injector-for-android)（直接通过attach agent来实现对debuggable app(release app需root)的动态代码注入）

<br/>

### 效果预览：

**将要注入的代码：**

```kotlin
fun showDialog() {
    thread {
        Handler(Looper.getMainLooper()).post {
            runningActivities.forEach {
                if (!it.isDestroyed) {
                    AlertDialog.Builder(it).setMessage("Hello Debugger from $application")
                        .setPositiveButton("close", null).show()
                }
            }
            Toast.makeText(application, "dialog has been showed", Toast.LENGTH_LONG).show()
        }
    }
}
```

>完整代码请移步: [Drug.kt](https://github.com/wuyr/jdwp-injector-for-android/blob/master/app/src/main/java/com/wuyr/jdwp_injector_test/Drug.kt)

<br/>

**运行效果：(需要科学上网)**

![preview](https://github.com/wuyr/jdwp-injector-for-android/raw/main/previews/1.gif)
![preview](https://github.com/wuyr/jdwp-injector-for-android/raw/main/previews/2.gif)
![preview](https://github.com/wuyr/jdwp-injector-for-android/raw/main/previews/3.gif)


>注：如果要注入release版的app，必须开启全局调试(`ro.debuggable=1`)或者当前系统类型是userdebug或eng(`ro.build.type=userdebug|eng`)才可以。
> **android 14之后改了判定机制，原来的`ro.debuggable`属性已经没用了，新的全局调试条件改成了`ro.build.type=eng`或者`ro.build.type=userdebug`并且`persist.debug.dalvik.vm.jdwp.enabled=1`**

<br/>

### Demo下载: [app-debug.apk](https://github.com/wuyr/jdwp-injector-for-android/raw/main/app-debug.apk)

<br/>

### 诞生背景：
去年年中因一次偶然的机会发现了某款国产车的车机系统存在一个惊天大漏洞(不知道是不是厂商故意开放出来钓鱼的)：它居然将一个sharedUserId为`android.uid.system`的系统常驻进程(`android:persistent="true"`)的`android:debuggable`属性设置为`"true"`了！！！
妈呀，这是什么概念？！ 这就意味着，可以借助debug来间接获取到`android.uid.system`(也就是俗称的系统级)权限，轻松实现很多普通app无法实现的功能！
不过，其局限性也很明显，就是不能实现自动化，因为每次要用到system权限时都需要依赖连接电脑才能获得。

**大胆的想法：** 由于此前有学习过 [Shizuku](https://github.com/RikkaApps/Shizuku) 的源码，对其中的adb通讯协议有一定的了解，再加上之前想调试zygote进程的时候，也研究过一阵子的jdwp(最后没搞成功)，有一天忽然想到：平时debug也是建立在adb的基础上，现在adb已经有人在android端实现了，那能不能把jdwp协议也搬过来呢？那样的话不就可以脱离对电脑的依赖，直接从app上发起debug，实现代码注入了？理论上是可行的，因为它就只是个协议，跟平台无关。

于是开始查阅相关资料，先看了[jdi](https://aosp.app/android-11.0.0_r1/xref/external/oj-libjdwp/)的代码，然后在cs.android.com上翻到了这个: [JDWP.java](https://cs.android.com/android/platform/superproject/main/+/main:out/soong/.intermediates/external/oj-libjdwp/jdwp_generated_java/gen/JDWP.java)，还在oracle官网上找到了这个: [jdwp-spec](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html) 以及这个: [jdwp-protocol](https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html)  文档。
基于以上资料，很快写出了一个简单的demo，为了方便调试，刚开始都是通过`adb forward tcp:<port> jdwp:<pid>`转发jdwp端口到pc，然后用socket来连接本地端口进行测试。
当一切准备得差不多，开始push到android设备运行的时候，我傻眼了，android端的adb居然没有`forward`这个命令！
后来通过[SERVICES.TXT](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/SERVICES.TXT#219)得知，`forward`命令是主机端(*HOST SERVICES*)才有提供，但我们在android端的实现，是属于本地端(*LOCAL SERVICES*)，这就尴尬了，要是找不到解决的办法，前面不白忙活了！
但好在，在*SERVICES.TXT*下面又看到了这个：
>jdwp:<pid>
>Connects to the JDWP thread running in the VM of process <pid>.

原来是通过`jdwp:<pid>`命令来连接到jdwp线程。但当我通过这个命令连接成功之后，发送握手消息，却没有收到jdwp线程的回复！这就奇怪了，明明都是同样的代码，为什么在电脑上跑就没问题，到了手机上运行就不行呢？
后面突然反应过来，通过`adb forward tcp:<port> jdwp:<pid>`命令创建的本地socket，本质上还是在和adb server通讯，所发出的消息还是要经过adb server转发给adbd，所以它依然是走的adb协议！也就是说，要通过adb数据包来传输jdwp数据包，像这样：adb数据包(jdwp数据包(数据))。

给握手消息外面套一层adb消息之后，果然可以了……

更多详细内容，请移步上面的博客链接。

<br/>

### 大致原理：
原理非常简单: 利用debugger的 *evaluate expression* 功能执行一段加载外部dex的代码，从而实现代码注入。

<br/>

### 附: jdwp工作流程：
每当zygote孵化新进程，静态函数`SpecializeCommon`([Zygote.cpp#1603](https://aosp.app/android-11.0.0_r1/xref/frameworks/base/core/jni/com_android_internal_os_Zygote.cpp#1603))被执行时，会进而调用到`Runtime::InitNonZygoteOrPostFork`([runtime.cc#1086](https://aosp.app/android-11.0.0_r1/xref/art/runtime/runtime.cc#1086))，如果app是可调试的，会启动一个线程 ——

运行命令: `adb shell ps -T [pid]`，你会发现可调试的app，会多了一个名字是 "ADB-JDWP Connec" 的线程，其实全名叫**ADB-JDWP Connection Control Thread**([adbconnection.h#39](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.h#39))，它就是负责和adbd服务通讯的jdwp线程。

这个线程在启动之后，(`control_socket`)会主动连接到adbd服务创建的一个叫*\0jdwp-control*的socket服务(unix域套接字)([adbconnection_client.cpp#111](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/libs/adbconnection/adbconnection_client.cpp#111))，接着传输所在进程`pid`、`debuggable`等信息（adbd那边收到之后会把这些信息存放在`_jdwp_list`里([jdwp_service.cpp#430](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/daemon/jdwp_service.cpp#430))），然后进入待命状态，等待adbd服务发来消息。

然后到【debugger】那边：
当【debugger】开始attach的时候，会通过adb server给adbd服务发送一条`A_OPEN jdwp:<被调试进程id>`消息，adbd收到这条消息之后([adb.cpp#416](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/adb.cpp#416) ---> [sockets.cpp#409](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/sockets.cpp#409) ---> [services.cpp#87](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/services.cpp#87) ---> [services.cpp#317](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/daemon/services.cpp#317))，会从`_jdwp_list`里面去查找对应的pid然后创建socketpair并返回其中一个fd([jdwp_service.cpp#252](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/daemon/jdwp_service.cpp#252))，剩下的这个fd，则通过`control_socket`(上面提到的跟【被调试进程】的jdwp线程建立连接的那个socket)发送给【被调试进程】([jdwp_service.cpp#225](https://aosp.app/android-11.0.0_r1/xref/system/core/adb/daemon/jdwp_service.cpp#225))，【被调试进程】这边收到之后(赋值给`adb_connection_socket_`)([adbconnection.cc#603](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.cc#603))，它们(【debugger】和【被调试进程】)之间将会使用这个fd来直接通讯。注意！这时候它们还不算正式建立连接。

接下来，【被调试进程】这边会等待`adb_connection_socket_`(刚刚从`control_socket`收到的fd)的消息([adbconnection.cc#620](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.cc#620)) ——
当收到消息时，(第一条消息通常是握手消息) 如果未握手，会先进行握手：
读取14个字符：`['J', 'D', 'W', 'P', '-', 'H', 'a', 'n', 'd', 's', 'h', 'a', 'k', 'e']`，也就是`"JDWP-Handshake"`，读取到这条消息之后，`adb_connection_socket_`也会回复同样的消息，然后标记握手成功，等待下一条消息([adbconnection.cc#775](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.cc#775))；

接下来，如果`adb_connection_socket_`接收到ddm(Dalvik Debug Monitor)数据包，会调用[DdmServer.broadcast(1/\*CONNECTED\*/)](https://aosp.app/android-11.0.0_r1/xref/libcore/dalvik/src/main/java/org/apache/harmony/dalvik/ddmc/DdmServer.java#117)，通知ddm已连接。
如果此时*DdmServer*有注册Handler的话，还会调用[DdmServer.dispatch](https://aosp.app/android-11.0.0_r1/xref/libcore/dalvik/src/main/java/org/apache/harmony/dalvik/ddmc/DdmServer.java#150)方法，然后把返回的Chunk数据通过`adb_connection_socket_`([adbconnection.cc#740](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.cc#740))发送给【debugger】。

如果接收到非ddm数据包，就会加载一个agent: **libjdwp.so**([adbconnection.cc#685](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.cc#685))，准备把数据包交给这个agent来处理，这个agent会跟【被调试进程】通过LocalSocket保持连接。玩过jvmti的同学应该对这个agent这个字眼很熟悉，没错，android的调试功能正是基于jvmti实现的！所以接下来还有个基于jvmti的非root动态代码注入工具: [agent-injector-for-android](https://github.com/wuyr/agent-injector-for-android)，敬请期待。

当jdwp的agent成功启动之后，【被调试进程】会把`adb_connection_socket_`(刚刚从`control_socket`收到的fd) 发送给agent([adbconnection.cc#570](https://aosp.app/android-11.0.0_r1/xref/art/adbconnection/adbconnection.cc#570))，那么接下来，就由agent全权负责跟【debugger】的通讯了，也就是debug正式开始([debugInit.c#1493](https://aosp.app/android-11.0.0_r1/xref/external/oj-libjdwp/src/share/back/debugInit.c#1493))。

**通俗地概括一下：**

在整个流程中，总共有3个角色，分别是：
调试器【debugger】(相亲对象A)、【手机里的adbd系统服务】(媒婆)、【手机里被调试的app】(相亲对象B)。

1. 手机里每个app在启动的时候都会先跟adbd建立通讯(相亲对象B先加了媒婆的微信，并把自己的大致情况告诉媒婆)；

2. 当需要debug的时候(相亲对象A想找对象，媒婆来活了)，【debugger】通过一些介质(usb, wifi)和【adbd系统服务】建立连接(相亲对象A加上媒婆的微信)后，【adbd系统服务】会把这个连接的文件描述符转发给【手机里被调试的app】(媒婆把相亲对象A的微信名片分享给相亲对象B)；

3. 【手机里被调试的app】收到这个文件描述符之后，直接跟【debugger】通讯(相亲对象B收到名片之后，加上了相亲对象A的微信，然后他们就直接通过微信联系的，收发消息不需要经过媒婆了)。

<br/>

### 声明：
**此工具仅供学习研究，请勿用于非法用途！**

<br/>

### 感谢：

**[Shizuku](https://github.com/RikkaApps/Shizuku)：** jdwp-injector-for-android的adb通讯和无线配对部分，都有~~抄袭~~参考Shizuku的代码；