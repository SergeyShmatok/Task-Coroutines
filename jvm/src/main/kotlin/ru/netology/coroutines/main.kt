package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.Author
import ru.netology.coroutines.dto.Comment
import ru.netology.coroutines.dto.Post
import ru.netology.coroutines.dto.PostWithComments
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private const val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        // Вызов корутины с объявленными ранее параметрами
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        async { // Создает сопрограмму и возвращает ее будущий результат как реализацию Deferred.
                            //val commentAuthor = post.comment.forEach{ comment -> getAuthor(client, comment.authorId)}
                            val comments = getComments(client, post.id)
                            val authorsComment =  comments.map {getAuthor(client, it.authorId)}
                            PostWithComments(post, comments, getAuthor(client, post.authorId), authorsComment)
                            // По идее, возвращает инстансы класса PostWithComments,
                            // с полями, инициализированными полученными до этого постами и комментариями,
                            // посредством вызова getComments прямо в конструкторе, и передачей ему
                            // id текущего поста. По количеству полученных постов из getPosts.
                        }
                    }.awaitAll() // Вызовет метод await для всех (deferred) элементов коллекции.
                // Сначала будет отправлен запрос на получение постов. Затем, одновременно,
                // отправлено сразу несколько запросов на получение комментариев.
                // Т.е он не будет ждать сразу всех постов, как с Sequence🤔?
                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    // Кастомное расширение-функция для класса OkHttpClient
    return suspendCoroutine { continuation ->
        // Функция suspendCoroutine() в Kotlin позволяет создать функцию suspend из API
        // на основе обратного вызова. Она принимает лямбда-выражение с параметром продолжения
        // в качестве аргумента. Функция использует этот объект продолжения для приостановки
        // текущей сопрограммы до тех пор, пока не будет вызван обратный вызов.
        // Как только это произойдёт, объект продолжения возобновляет сопрограмму
        // и передаёт результат обратного вызова обратно вызывающему коду.
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                    // Продолжаем корутину с этим результатом
                    // continuation.resume - возобновляет выполнение соответствующей сопрограммы,
                    // передавая значение как возвращаемое значение последней точки приостановки.
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    // Использует инстанс ("client") OkHttpClient, чтобы вызвать apiCall, объявленную ранее
    withContext(Dispatchers.IO) { // Перечёркнутая стрелочка слева, — это вызов suspend функции
        // - Dispatchers.IO - определяет логику организации потоков. Для IO это пул тредов (64 потока)
        // А обращение к серверу, это же I/O операция
        // По идее ☝️, эта опция нужна только для выстраивания логики работы программы.
        // - withContext - вызывает указанный блок приостановки с заданным контекстом сопрограммы,
        // приостанавливает его до завершения и возвращает результат.
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})
// Просто вызывает makeRequest со своими настройками

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, id: Long): Author =
    makeRequest("$BASE_URL/api/authors/$id", client, object : TypeToken<Author>() {})

/*
fun main() {
    runBlocking {
        println(Thread.currentThread().name)
    }
}
*/

/*
fun main() {
    CoroutineScope(EmptyCoroutineContext).launch {
        println(Thread.currentThread().name)
    }

    Thread.sleep(1000L)
}
*/

/*
fun main() {
    val custom = Executors.newFixedThreadPool(64).asCoroutineDispatcher()
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch(Dispatchers.Default) {
            println(Thread.currentThread().name)
        }
        launch(Dispatchers.IO) {
            println(Thread.currentThread().name)
        }
        // will throw exception without UI
        // launch(Dispatchers.Main) {
        //    println(Thread.currentThread().name)
        // }

        launch(custom) {
            println(Thread.currentThread().name)
        }
    }
    Thread.sleep(1000L)
    custom.close()
}
*/

/*
private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        PostWithComments(post, getComments(client, post.id))
                    }
                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})
*/