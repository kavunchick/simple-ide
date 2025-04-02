package hyliavla.cvut.cz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import java.io.InputStream

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(Modifier.fillMaxWidth()) {
            Editor()
        }
    }
}

@Composable
fun Editor() {
    val editorState by remember { mutableStateOf(TextFieldState()) }
    var executionResult by remember { mutableStateOf(AnnotatedString("")) }
    val coroutineScope = rememberCoroutineScope()
    var execStatus by remember { mutableStateOf(Color.Red) }
    var returnValue by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    Scaffold(topBar = {
        TopAppBar(backgroundColor = Color.Black) {
            Button(
                modifier = Modifier.padding(start = 5.dp),
                onClick = {
                    execute(
                        coroutineScope,
                        editorState,
                        focusRequester,
                        { newResult -> executionResult = newResult },
                        { newStatus -> execStatus = newStatus },
                        { newReturnVal -> returnValue = newReturnVal })
                }) { Text("Run") }
            executableStatus(returnValue, execStatus)
        }
    }) {
        Row {
            BasicTextField(
                state = editorState,
                modifier = Modifier.fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(Color.DarkGray)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp)
            )
            Divider(color = Color.White, modifier = Modifier.fillMaxHeight().width(1.dp))
            Text(
                text = executionResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(Color.DarkGray)
                    .verticalScroll(rememberScrollState()),
                color = Color.White,
                fontSize = 20.sp
            )
        }
    }
}

/**
 * Processes the given input stream and updates the result.
 * @param ins The input stream to be processed.
 * @param onResultChange A callback function that is invoked with the updated result.
 */
fun processStream(ins: InputStream, onResultChange: (AnnotatedString) -> Unit) {
    var symbol: Int
    val builder = StringBuilder()
    var counter = 0
    while ((ins.read().also { symbol = it }) != -1) {
        ++counter
        builder.append(symbol.toChar())
        onResultChange(AnnotatedString(builder.toString()))
        if (counter == 100) {
            counter = 0
            System.out.flush()
            System.err.flush()
        }
    }
}

@Composable
fun executableStatus(returnValue: Int, status: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(modifier = Modifier.padding(end = 10.dp), text = "Exit value: $returnValue", color = Color.White)
        Text(modifier = Modifier.padding(end = 10.dp), text = "Status:", color = Color.White)
        Canvas(modifier = Modifier.size(10.dp), onDraw = { drawCircle(color = status) })
    }
}

/**
 * Formats the error output by making error locations clickable.
 * @param error The error message as an AnnotatedString.
 * @param editorState The state of the text editor.
 * @param focusRequester The focus requester to request focus on the editor.
 * @return An AnnotatedString with clickable error locations.
 */
fun formatErrorOutput(
    error: AnnotatedString,
    editorState: TextFieldState,
    focusRequester: FocusRequester
): AnnotatedString {
    val regex = "(?<=\\.kts:)(\\d+):(\\d+)".toRegex()
    val errorStrip = error.split("\n")
    return buildAnnotatedString {
        errorStrip.forEach {
            if (it.startsWith("foo.kts:")) {
                val (lineIdx, charIdxStr) = regex.find(it)!!.destructured
                val link = LinkAnnotation.Clickable(tag = "ERROR", linkInteractionListener = {
                    val text = editorState.text.split('\n')
                    editorState.edit {
                        var charIdx = charIdxStr.toInt()
                        for (i in 0 until lineIdx.toInt() - 1)
                            charIdx += text[i].length + 1
                        selection = TextRange(charIdx - 1)
                        focusRequester.requestFocus()
                    }
                }, styles = TextLinkStyles(SpanStyle(color = Color.Red)))
                withLink(link) { append(it + "\n") }
            } else { append(it + "\n") }
        }
    }
}

/**
 * Executes the Kotlin script and updates the result, return value, and status.
 * @param coroutineScope The coroutine scope to launch the execution in.
 * @param editorState The state of the text editor.
 * @param focusRequester The focus requester to request focus on the editor.
 * @param onResultChange A callback function that is invoked with the updated result as an AnnotatedString.
 * @param onStatusChange A callback function that is invoked with the updated status color.
 * @param onReturnValueChange A callback function that is invoked with the updated return value.
 */
fun execute(
    coroutineScope: CoroutineScope,
    editorState: TextFieldState,
    focusRequester: FocusRequester,
    onResultChange: (AnnotatedString) -> Unit,
    onStatusChange: (Color) -> Unit,
    onReturnValueChange: (Int) -> Unit
) {
    var error = AnnotatedString("")
    coroutineScope.launch(Dispatchers.IO) {
        onResultChange(AnnotatedString(""))
        File("foo.kts").writeText(editorState.text.toString())

        val processBuilder = ProcessBuilder("kotlinc", "-script", "foo.kts")
        val process = processBuilder.start()

        onStatusChange(Color.Green)
        processStream(process.inputStream) { newResult -> onResultChange(newResult) }
        processStream(process.errorStream) { newResult -> error = newResult }
        process.destroy()
        onStatusChange(Color.Red)

        val returnVal = process.exitValue()
        onReturnValueChange(returnVal)

        if (returnVal != 0)
            onResultChange(formatErrorOutput(error, editorState, focusRequester))
    }
}
