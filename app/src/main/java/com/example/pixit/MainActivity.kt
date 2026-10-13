package com.example.pixit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// ---------- Data ----------
val Palette = listOf(0xFF000000, 0xFFFFFFFF, 0xFFFF3B7F, 0xFFFFC53D, 0xFF3DDC84, 0xFF3D8BFF, 0xFFA855F7, 0xFFFF7A3D).map { it.toInt() }
val Pink = Color(0xFFFF3B7F)
val Mono = FontFamily.Monospace

fun sprite(seed: Int, n: Int = 12): List<Int> {
    val r = Random(seed); val c = Palette[2 + r.nextInt(6)]; val l = MutableList(n * n) { 0 }
    for (y in 0 until n) for (x in 0 until n / 2) if (r.nextFloat() < 0.5f) { l[y * n + x] = c; l[y * n + n - 1 - x] = c }
    return l
}

class Post(val id: Int, val author: String, val px: List<Int>, likes: Int, val ephemeral: Boolean, val cat: String) {
    var likes by mutableIntStateOf(likes); var liked by mutableStateOf(false)
}
class Msg(val mine: Boolean, val text: String? = null, val art: List<Int>? = null)

object Store {
    val cats = listOf("Avatars", "Memes", "Mini-scenes")
    val users = listOf("mochi", "byte", "glitch", "nova", "tofu", "zap", "sprout", "kirby8")
    val posts = mutableStateListOf<Post>().apply {
        users.forEachIndexed { i, u -> add(Post(i, u, sprite(i + 1), Random(i).nextInt(5, 300), i % 4 == 3, cats[i % 3])) }
    }
    val friends = mutableStateListOf("mochi", "byte", "glitch")
    val avatar = mutableStateListOf<Int>().apply { addAll(sprite(777, 8)) }
    val best = mutableStateMapOf("Snake" to 0, "Reflex" to 0)
    val chats = mutableStateMapOf<String, SnapshotStateListOfMsg>()
    fun chat(u: String) = chats.getOrPut(u) { mutableStateListOf(Msg(false, "hey! draw with me? 👾")) }
    fun submit(game: String, s: Int) { if (s > (best[game] ?: 0)) best[game] = s }
    val xp get() = best.values.sum() / 10 + friends.size * 5 + posts.count { it.author == "me" } * 10
    fun trophies() = buildList {
        if (posts.any { it.author == "me" }) add("First Pixel")
        if (friends.size >= 4) add("Social Butterfly")
        if ((best["Snake"] ?: 0) >= 5) add("Snake Charmer")
        if ((best["Reflex"] ?: 0) >= 700) add("Quick Draw")
        if (xp >= 50) add("Pixel Pro")
    }
}
typealias SnapshotStateListOfMsg = androidx.compose.runtime.snapshots.SnapshotStateList<Msg>

// ---------- Theme ----------
@Composable
fun PixitTheme(dark: Boolean, content: @Composable () -> Unit) {
    val cs = if (dark) darkColorScheme(background = Color.Black, surface = Color.Black, onBackground = Color.White, onSurface = Color.White, primary = Pink)
    else lightColorScheme(background = Color.White, surface = Color.White, onBackground = Color.Black, onSurface = Color.Black, primary = Pink)
    MaterialTheme(colorScheme = cs) { Surface(color = cs.background, contentColor = cs.onBackground, content = content) }
}

// ---------- Reusable pixel widgets ----------
@Composable fun Ico(id: Int, size: Dp = 24.dp, tint: Color = LocalContentColor.current, modifier: Modifier = Modifier) =
    Icon(painterResource(id), null, modifier.size(size), tint)

@Composable
fun PixelGrid(px: List<Int>, n: Int, modifier: Modifier = Modifier, onPaint: ((Int) -> Unit)? = null) {
    val fg = MaterialTheme.colorScheme.onBackground
    val cb by rememberUpdatedState(onPaint)
    var m = modifier.aspectRatio(1f).border(2.dp, fg)
    if (onPaint != null) m = m
        .pointerInput(n) { detectTapGestures { o -> cb?.invoke(idx(o, size.width, n)) } }
        .pointerInput(n) { detectDragGestures { ch, _ -> cb?.invoke(idx(ch.position, size.width, n)) } }
    Canvas(m) {
        val cell = size.width / n
        px.forEachIndexed { i, c -> if (c != 0) drawRect(Color(c), Offset((i % n) * cell, (i / n) * cell), Size(cell + 0.5f, cell + 0.5f)) }
        if (onPaint != null) for (i in 1 until n) {
            drawLine(fg.copy(alpha = .1f), Offset(i * cell, 0f), Offset(i * cell, size.height))
            drawLine(fg.copy(alpha = .1f), Offset(0f, i * cell), Offset(size.width, i * cell))
        }
    }
}
private fun idx(o: Offset, w: Int, n: Int): Int {
    val x = (o.x / w * n).toInt().coerceIn(0, n - 1); val y = (o.y / w * n).toInt().coerceIn(0, n - 1); return y * n + x
}

@Composable
fun ColorBar(sel: Int, onSel: (Int) -> Unit) = Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
    (Palette + 0).forEach { c ->
        Box(Modifier.weight(1f).aspectRatio(1f).background(if (c == 0) Color.Transparent else Color(c))
            .border(if (c == sel) 3.dp else 1.dp, if (c == sel) Pink else MaterialTheme.colorScheme.onBackground)
            .clickable { onSel(c) }, Alignment.Center) { if (c == 0) Text("X", fontFamily = Mono, fontSize = 11.sp) }
    }
}

@Composable
fun PBtn(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    Box(modifier.border(2.dp, fg).background(if (filled) Pink else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), Alignment.Center) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (filled) Color.White else fg)
    }
}
@Composable fun H(t: String) = Text(t.uppercase(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
@Composable fun T(t: String, s: Int = 13, c: Color = Color.Unspecified) = Text(t, fontFamily = Mono, fontSize = s.sp, color = c)

// ---------- App shell ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

@Composable
fun App() {
    var dark by rememberSaveable { mutableStateOf(true) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    PixitTheme(dark) {
        val fg = MaterialTheme.colorScheme.onBackground
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Box(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).drawBottom(fg), Alignment.Center) {
                    Image(painterResource(R.drawable.logo), "Pixit", Modifier.height(26.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg), contentScale = ContentScale.Fit)
                    Ico(R.drawable.ic_post, 28.dp, Pink, Modifier.align(Alignment.CenterStart).padding(start = 16.dp).clickable { overlay = "editor" })
                    Ico(R.drawable.ic_profile, 28.dp, fg, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).clickable { overlay = "profile" })
                }
            },
            bottomBar = {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp).drawTop(fg), Arrangement.SpaceAround, Alignment.CenterVertically) {
                    listOf(R.drawable.ic_home, R.drawable.ic_explore, R.drawable.ic_chat, R.drawable.ic_like, R.drawable.ic_games).forEachIndexed { i, ic ->
                        val on = tab == i && overlay == null
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { tab = i; overlay = null }, Alignment.Center) {
                            Ico(ic, 30.dp, if (on) Pink else fg.copy(alpha = .6f))
                            if (on) Box(Modifier.align(Alignment.BottomCenter).size(16.dp, 4.dp).background(Pink))
                        }
                    }
                }
            }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (overlay) {
                    "profile" -> Profile(dark, { dark = it }) { overlay = null }
                    "editor" -> Editor { overlay = null; tab = 0 }
                    else -> when (tab) { 0 -> Home(); 1 -> Explore(); 2 -> Chat(); 3 -> Notifications(); else -> Games() }
                }
            }
        }
    }
}
fun Modifier.drawBottom(c: Color) = drawBehind { drawRect(c, Offset(0f, size.height - 3f), Size(size.width, 3f)) }
fun Modifier.drawTop(c: Color) = drawBehind { drawRect(c, Offset.Zero, Size(size.width, 3f)) }

// ---------- Home ----------
@Composable
fun PostCard(p: Post) {
    val fg = MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxWidth().padding(12.dp, 6.dp).border(2.dp, fg).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelGrid(sprite(p.author.hashCode(), 8), 8, Modifier.size(28.dp)); Spacer(Modifier.width(8.dp))
            T("@${p.author}", 14); Spacer(Modifier.weight(1f))
            if (p.ephemeral) T("⏳ 24h", 11, Pink)
        }
        Spacer(Modifier.height(8.dp))
        PixelGrid(p.px, if (p.px.size == 256) 16 else 12, Modifier.fillMaxWidth())
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ic_like, 26.dp, if (p.liked) Pink else fg, Modifier.clickable { p.liked = !p.liked; p.likes += if (p.liked) 1 else -1 })
            Spacer(Modifier.width(8.dp)); T("${p.likes}"); Spacer(Modifier.weight(1f)); T("#${p.cat.lowercase()}", 11)
        }
    }
}
@Composable fun Home() = LazyColumn { items(Store.posts.toList()) { PostCard(it) } }

// ---------- Editor ----------
@Composable
fun Editor(done: () -> Unit) {
    val px = remember { mutableStateListOf<Int>().apply { addAll(List(256) { 0 }) } }
    var color by remember { mutableIntStateOf(Palette[2]) }; var eph by remember { mutableStateOf(false) }; var cat by remember { mutableStateOf(Store.cats[0]) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H("New pixel post")
        PixelGrid(px, 16, Modifier.fillMaxWidth()) { px[it] = color }
        ColorBar(color) { color = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Store.cats.forEach { c -> PBtn(c, filled = c == cat) { cat = c } } }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(eph, { eph = it }); Spacer(Modifier.width(8.dp)); T(if (eph) "Ephemeral (24h)" else "Permanent") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PBtn("CLEAR", filled = false) { for (i in px.indices) px[i] = 0 }
            PBtn("CANCEL", filled = false, onClick = done)
            PBtn("POST") { Store.posts.add(0, Post(Random.nextInt(), "me", px.toList(), 0, eph, cat)); done() }
        }
    }
}

// ---------- Explore ----------
@Composable
fun Explore() {
    var q by remember { mutableStateOf("") }; var f by remember { mutableStateOf("All") }
    val list = Store.posts.filter { (f == "All" || it.cat == f) && (q.isBlank() || it.author.contains(q, true) || it.cat.contains(q, true)) }.sortedByDescending { it.likes }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { T("search art & creators") }, singleLine = true,
            leadingIcon = { Ico(R.drawable.ic_search, 20.dp) }, shape = androidx.compose.ui.graphics.RectangleShape)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (listOf("All") + Store.cats).forEach { c -> PBtn(c, filled = c == f) { f = c } } }
        H("Trending creators")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Store.users) { u ->
                val isF = u in Store.friends
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelGrid(sprite(u.hashCode(), 8), 8, Modifier.size(56.dp)); T(u, 11)
                    PBtn(if (isF) "−" else "+", filled = !isF) { if (isF) Store.friends.remove(u) else Store.friends.add(u) }
                }
            }
        }
        H("Trending pixel art")
        LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gridItems(list) { p -> Column { PixelGrid(p.px, if (p.px.size == 256) 16 else 12, Modifier.fillMaxWidth()); T("@${p.author} ♥${p.likes}", 11) } }
        }
    }
}

// ---------- Chat ----------
@Composable
fun Chat() {
    var open by remember { mutableStateOf<String?>(null) }
    if (open == null) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            H("Chats")
            if (Store.friends.isEmpty()) T("Add friends in Explore!")
            LazyColumn { items(Store.friends.toList()) { u ->
                Row(Modifier.fillMaxWidth().clickable { open = u }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelGrid(sprite(u.hashCode(), 8), 8, Modifier.size(40.dp)); Spacer(Modifier.width(10.dp)); T("@$u", 15)
                }
            } }
        }
    } else ChatRoom(open!!) { open = null }
}

@Composable
fun ChatRoom(u: String, back: () -> Unit) {
    val msgs = Store.chat(u); var text by remember { mutableStateOf("") }; var co by remember { mutableStateOf(false) }
    var color by remember { mutableIntStateOf(Palette[2]) }; var priv by remember { mutableStateOf(true) }
    val canvas = remember { mutableStateListOf<Int>().apply { addAll(List(144) { 0 }) } }
    LaunchedEffect(co) { while (co) { delay(700); canvas[Random.nextInt(144)] = Palette[Random.nextInt(2, 8)] } } // simulated friend strokes
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { PBtn("<", filled = false, onClick = back); Spacer(Modifier.width(8.dp)); H("@$u"); Spacer(Modifier.weight(1f))
            PBtn(if (co) "STOP DRAW" else "CO-DRAW", filled = co) { co = !co } }
        if (co) {
            PixelGrid(canvas, 12, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally)) { canvas[it] = color }
            ColorBar(color) { color = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(priv, { priv = it }); T(if (priv) "private" else "public"); Spacer(Modifier.weight(1f))
                PBtn("SEND ART") { msgs.add(Msg(true, art = canvas.toList())) }
            }
        }
        LazyColumn(Modifier.weight(1f), reverseLayout = true) { items(msgs.reversed()) { m ->
            Row(Modifier.fillMaxWidth().padding(4.dp), if (m.mine) Arrangement.End else Arrangement.Start) {
                Box(Modifier.border(2.dp, if (m.mine) Pink else MaterialTheme.colorScheme.onBackground).padding(8.dp)) {
                    if (m.art != null) PixelGrid(m.art, if (m.art.size == 144) 12 else 8, Modifier.size(80.dp)) else T(m.text ?: "")
                }
            }
        } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items((101..108).toList()) { s ->
            PixelGrid(sprite(s), 12, Modifier.size(40.dp).clickable { msgs.add(Msg(true, art = sprite(s))) })
        } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true, shape = androidx.compose.ui.graphics.RectangleShape)
            Spacer(Modifier.width(6.dp)); PBtn("SEND") { if (text.isNotBlank()) { msgs.add(Msg(true, text)); text = "" } }
        }
    }
}

// ---------- Notifications (like icon) ----------
@Composable
fun Notifications() = Column(Modifier.fillMaxSize().padding(12.dp)) {
    H("Likes & activity")
    LazyColumn { items(Store.users.take(6)) { u ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ic_like, 22.dp, Pink); Spacer(Modifier.width(8.dp)); PixelGrid(sprite(u.hashCode(), 8), 8, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
            T("@$u liked your pixel", 13)
        }
    } }
}

// ---------- Games ----------
@Composable
fun Games() {
    var g by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (g == null) {
            H("Pixel arcade")
            listOf("Snake", "Reflex", "Tetris").forEach { n ->
                Row(Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.onBackground).clickable(enabled = n != "Tetris") { g = n }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ic_games, 28.dp); Spacer(Modifier.width(12.dp)); T(n.uppercase(), 16); Spacer(Modifier.weight(1f))
                    T(if (n == "Tetris") "soon" else "best ${Store.best[n]}", 12, Pink)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) { PBtn("<", filled = false) { g = null }; Spacer(Modifier.width(8.dp)); H(g!!) }
            if (g == "Snake") Snake { Store.submit("Snake", it) } else Reflex { Store.submit("Reflex", it) }
            H("Leaderboard")
            val fake = listOf("mochi" to 9, "byte" to 6, "nova" to 3).map { (n, s) -> n to if (g == "Snake") s else s * 110 }
            (fake + ("you" to (Store.best[g] ?: 0))).sortedByDescending { it.second }.forEachIndexed { i, (n, s) -> T("${i + 1}. @$n — $s", 13, if (n == "you") Pink else Color.Unspecified) }
        }
    }
}

@Composable
fun Snake(onScore: (Int) -> Unit) {
    val n = 16
    var snake by remember { mutableStateOf(listOf(8 to 8)) }; var dir by remember { mutableStateOf(1 to 0) }
    var food by remember { mutableStateOf(3 to 3) }; var dead by remember { mutableStateOf(false) }; var run by remember { mutableIntStateOf(0) }
    LaunchedEffect(run) {
        snake = listOf(8 to 8); dir = 1 to 0; dead = false
        while (!dead) {
            delay(140); val h = snake.first(); val nh = (h.first + dir.first) to (h.second + dir.second)
            if (nh.first !in 0 until n || nh.second !in 0 until n || nh in snake) { dead = true; onScore(snake.size - 1) }
            else if (nh == food) { snake = listOf(nh) + snake; food = Random.nextInt(n) to Random.nextInt(n) }
            else snake = listOf(nh) + snake.dropLast(1)
        }
    }
    val px = List(n * n) { i -> val p = i % n to i / n; if (p == food) Palette[2] else if (p in snake) Palette[4] else 0 }
    PixelGrid(px, n, Modifier.fillMaxWidth())
    T(if (dead) "GAME OVER — score ${snake.size - 1}" else "score ${snake.size - 1}", 14)
    fun turn(d: Pair<Int, Int>) { if (d.first != -dir.first || d.second != -dir.second) dir = d }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PBtn("▲") { turn(0 to -1) }
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) { PBtn("◀") { turn(-1 to 0) }; PBtn("▶") { turn(1 to 0) } }
        PBtn("▼") { turn(0 to 1) }
        if (dead) PBtn("RETRY", Modifier.padding(top = 6.dp)) { run++ }
    }
}

@Composable
fun Reflex(onScore: (Int) -> Unit) {
    var state by remember { mutableIntStateOf(0) }; var t0 by remember { mutableLongStateOf(0L) }; var ms by remember { mutableIntStateOf(0) }; var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) { if (state == 1) { delay(Random.nextLong(1000, 3500)); if (state == 1) { state = 2; t0 = System.currentTimeMillis() } } }
    val (bg, label) = when (state) { 0 -> Color.DarkGray to "TAP TO START"; 1 -> Pink to "WAIT..."; 2 -> Palette[4].let { Color(it) } to "TAP NOW!"
        else -> Color.DarkGray to (if (ms < 0) "TOO EARLY! tap to retry" else "$ms ms — score ${(1000 - ms).coerceAtLeast(0)}") }
    Box(Modifier.fillMaxWidth().height(260.dp).background(bg).border(2.dp, MaterialTheme.colorScheme.onBackground).clickable {
        when (state) {
            0, 3 -> { state = 1; round++ }
            1 -> { ms = -1; state = 3 }
            2 -> { ms = (System.currentTimeMillis() - t0).toInt(); state = 3; onScore((1000 - ms).coerceAtLeast(0)) }
        }
    }, Alignment.Center) { Text(label, fontFamily = Mono, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp) }
}

// ---------- Profile ----------
@Composable
fun Profile(dark: Boolean, setDark: (Boolean) -> Unit, back: () -> Unit) {
    var color by remember { mutableIntStateOf(Palette[2]) }
    val trophies = Store.trophies()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row { PBtn("<", filled = false, onClick = back); Spacer(Modifier.weight(1f)); Ico(R.drawable.ic_settings, 26.dp) } }
        item {
            H("My pixel avatar (tap to edit)")
            PixelGrid(Store.avatar, 8, Modifier.fillMaxWidth(0.5f)) { Store.avatar[it] = color }
            Spacer(Modifier.height(6.dp)); ColorBar(color) { color = it }
        }
        item { T("@me · ${Store.friends.size} friends · ${Store.xp} XP", 14, Pink)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(dark, setDark); Spacer(Modifier.width(8.dp)); T(if (dark) "Dark (black)" else "Light (white)") } }
        item { H("Trophies"); if (trophies.isEmpty()) T("Play games, post & add friends to earn trophies") else trophies.forEach { T("🏆 $it", 14) } }
        item { H("Collectibles"); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items((201..200 + 2 + trophies.size * 2).toList()) { PixelGrid(sprite(it), 12, Modifier.size(48.dp)) } } }
        item { H("Friends") }
        items(Store.friends.toList()) { u -> Row(verticalAlignment = Alignment.CenterVertically) { PixelGrid(sprite(u.hashCode(), 8), 8, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp)); T("@$u", 14); Spacer(Modifier.weight(1f)); PBtn("REMOVE", filled = false) { Store.friends.remove(u) } } }
        item { H("My posts") }
        items(Store.posts.filter { it.author == "me" }) { PostCard(it) }
    }
}
