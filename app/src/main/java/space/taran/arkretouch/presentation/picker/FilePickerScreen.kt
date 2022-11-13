package space.taran.arkretouch.presentation.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import space.taran.arkfilepicker.ArkFilePickerConfig
import space.taran.arkfilepicker.ArkFilePickerFragment
import space.taran.arkfilepicker.ArkFilePickerMode
import space.taran.arkfilepicker.onArkPathPicked
import space.taran.arkretouch.R
import space.taran.arkretouch.presentation.theme.Purple500
import space.taran.arkretouch.presentation.theme.Purple700
import java.nio.file.Path

@Composable
fun PickerScreen(
    fragmentManager: FragmentManager,
    onNavigateToEdit: (Path?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth()
                .padding(20.dp)
                .onSizeChanged {
                    size = it
                }
                .clip(RoundedCornerShape(10))
                .background(Purple500)
                .clickable {
                    ArkFilePickerFragment
                        .newInstance(imageFilePickerConfig())
                        .show(fragmentManager, null)
                    fragmentManager.onArkPathPicked(lifecycleOwner) {
                        onNavigateToEdit(it)
                    }
                }
                .border(2.dp, Purple700, shape = RoundedCornerShape(10)),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.open),
                fontSize = 24.sp,
                color = Color.White
            )
            Icon(
                modifier = Modifier.size(size.height.toDp() / 2),
                imageVector = ImageVector.vectorResource(R.drawable.ic_insert_photo),
                tint = Color.White,
                contentDescription = null
            )
        }
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
            text = stringResource(R.string.or),
            fontSize = 24.sp
        )
        Column(modifier = Modifier
            .weight(2f)
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(10))
            .background(Purple500)
            .clickable {
                onNavigateToEdit(null)
            }
            .border(2.dp, Purple700, shape = RoundedCornerShape(10)),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.new_),
                fontSize = 24.sp,
                color = Color.White
            )
            Icon(
                modifier = Modifier
                    .size(size.height.toDp() / 2),
                imageVector = ImageVector.vectorResource(R.drawable.ic_add),
                tint = Color.White,
                contentDescription = null
            )
        }
    }

}

fun imageFilePickerConfig(initPath: Path? = null) = ArkFilePickerConfig(
    mode = ArkFilePickerMode.FILE,
    initialPath = initPath
)

@Composable
fun Int.toDp() = with(LocalDensity.current) {
    this@toDp.toDp()
}

@Composable
fun Dp.toPx() = with(LocalDensity.current) {
    this@toPx.toPx()
}