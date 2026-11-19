package xyz.mpv.rex.feature.webshare

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Standard-compliant, self-contained QR Code matrix generator in pure Kotlin (ISO/IEC 18004).
 * Supports Byte-mode encoding with Reed-Solomon Error Correction (Level M)
 * and all 8 standard mask penalty evaluations.
 */
object QrCodeGenerator {

  fun generateMatrix(content: String): Array<BooleanArray> {
    val textBytes = content.toByteArray(Charsets.UTF_8)
    val version = selectVersion(textBytes.size)
    val dim = 17 + version * 4

    // 1. Bit buffer & Byte mode encoding
    val bitBuffer = mutableListOf<Int>()
    appendBits(bitBuffer, 0b0100, 4) // Byte mode indicator
    val countBits = if (version <= 9) 8 else 16
    appendBits(bitBuffer, textBytes.size, countBits)
    for (b in textBytes) {
      appendBits(bitBuffer, b.toInt() and 0xFF, 8)
    }

    val totalCodewords = NUM_RAW_DATA_MODULES[version] / 8
    val numBlocks = ECC_BLOCKS_LEVEL_M[version]
    val ecLenPerBlock = ECC_CODEWORDS_PER_BLOCK_LEVEL_M[version]
    val totalEcCodewords = numBlocks * ecLenPerBlock
    val totalDataCodewords = totalCodewords - totalEcCodewords

    // Terminator
    val capacityBits = totalDataCodewords * 8
    val terminatorLength = min(4, capacityBits - bitBuffer.size)
    appendBits(bitBuffer, 0, terminatorLength)

    // Pad to byte boundary
    while (bitBuffer.size % 8 != 0) {
      bitBuffer.add(0)
    }

    // Pad bytes 0xEC, 0x11
    val padBytes = intArrayOf(0xEC, 0x11)
    var padIdx = 0
    while (bitBuffer.size < capacityBits) {
      appendBits(bitBuffer, padBytes[padIdx % 2], 8)
      padIdx++
    }

    // Convert bit buffer to data codewords array
    val dataCodewords = IntArray(totalDataCodewords)
    for (i in 0 until totalDataCodewords) {
      var byteVal = 0
      for (b in 0 until 8) {
        byteVal = (byteVal shl 1) or bitBuffer[i * 8 + b]
      }
      dataCodewords[i] = byteVal
    }

    // 2. Reed-Solomon Error Correction & Block Interleaving
    val shortBlockLen = totalDataCodewords / numBlocks
    val numShortBlocks = numBlocks - (totalDataCodewords % numBlocks)
    val numLongBlocks = totalDataCodewords % numBlocks
    val longBlockLen = shortBlockLen + 1

    val dataBlocks = Array(numBlocks) { b ->
      val len = if (b < numShortBlocks) shortBlockLen else longBlockLen
      IntArray(len)
    }
    var dataIdx = 0
    for (b in 0 until numBlocks) {
      for (i in 0 until dataBlocks[b].size) {
        dataBlocks[b][i] = dataCodewords[dataIdx++]
      }
    }

    val rsGenPoly = reedSolomonGeneratorPolynomial(ecLenPerBlock)
    val ecBlocks = Array(numBlocks) { b ->
      reedSolomonComputeRemainder(dataBlocks[b], rsGenPoly)
    }

    val finalCodewords = IntArray(totalCodewords)
    var outIdx = 0
    val maxDataLen = if (numLongBlocks > 0) longBlockLen else shortBlockLen
    for (i in 0 until maxDataLen) {
      for (b in 0 until numBlocks) {
        if (i < dataBlocks[b].size) {
          finalCodewords[outIdx++] = dataBlocks[b][i]
        }
      }
    }
    for (i in 0 until ecLenPerBlock) {
      for (b in 0 until numBlocks) {
        finalCodewords[outIdx++] = ecBlocks[b][i]
      }
    }

    // 3. Matrix & Function Patterns
    val matrix = Array(dim) { BooleanArray(dim) }
    val isFunction = Array(dim) { BooleanArray(dim) }

    placeFinderPatterns(matrix, isFunction, dim)
    placeTimingPatterns(matrix, isFunction, dim)
    placeAlignmentPatterns(matrix, isFunction, version, dim)
    placeDarkModule(matrix, isFunction, dim)
    reserveFormatAndVersionInfo(isFunction, version, dim)

    // 4. Place Data Bits
    placeDataBits(matrix, isFunction, finalCodewords, dim)

    // 5. Mask Selection (choose mask with lowest penalty)
    var bestMask = 0
    var minPenalty = Int.MAX_VALUE
    var bestMatrix = Array(dim) { BooleanArray(dim) }

    for (mask in 0..7) {
      val maskedMatrix = Array(dim) { r ->
        BooleanArray(dim) { c ->
          if (isFunction[r][c]) {
            matrix[r][c]
          } else {
            val bit = matrix[r][c]
            if (maskPattern(mask, r, c)) !bit else bit
          }
        }
      }
      placeFormatInfo(maskedMatrix, dim, mask)
      if (version >= 7) {
        placeVersionInfo(maskedMatrix, dim, version)
      }

      val penalty = calculatePenalty(maskedMatrix, dim)
      if (penalty < minPenalty) {
        minPenalty = penalty
        bestMask = mask
        bestMatrix = maskedMatrix
      }
    }

    // 6. Add Quiet Zone (4 modules on all sides)
    val quietZone = 4
    val finalDim = dim + quietZone * 2
    val result = Array(finalDim) { BooleanArray(finalDim) }
    for (r in 0 until dim) {
      for (c in 0 until dim) {
        result[r + quietZone][c + quietZone] = bestMatrix[r][c]
      }
    }

    return result
  }

  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
    val matrix = generateMatrix(content)
    val matrixSize = matrix.size
    val scale = (sizePx / matrixSize).coerceAtLeast(1)
    val actualSize = matrixSize * scale

    val pixels = IntArray(actualSize * actualSize)
    val black = Color.Black.toArgb()
    val white = Color.White.toArgb()

    for (y in 0 until actualSize) {
      val my = y / scale
      val rowOffset = y * actualSize
      for (x in 0 until actualSize) {
        val mx = x / scale
        val isBlack = matrix[my][mx]
        pixels[rowOffset + x] = if (isBlack) black else white
      }
    }

    val bitmap = Bitmap.createBitmap(actualSize, actualSize, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, actualSize, 0, 0, actualSize, actualSize)
    return bitmap
  }

  // --- Function Patterns Placement ---

  private fun placeFinderPatterns(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, dim: Int) {
    placeOneFinder(matrix, isFunc, 0, 0)
    placeOneFinder(matrix, isFunc, dim - 7, 0)
    placeOneFinder(matrix, isFunc, 0, dim - 7)

    // Horizontal & vertical separators around finders
    // Top-left
    for (i in 0..7) {
      setFunctionModule(matrix, isFunc, 7, i, false)
      setFunctionModule(matrix, isFunc, i, 7, false)
    }
    // Top-right
    for (i in 0..7) {
      setFunctionModule(matrix, isFunc, 7, dim - 1 - i, false)
      setFunctionModule(matrix, isFunc, i, dim - 8, false)
    }
    // Bottom-left
    for (i in 0..7) {
      setFunctionModule(matrix, isFunc, dim - 8, i, false)
      setFunctionModule(matrix, isFunc, dim - 1 - i, 7, false)
    }
  }

  private fun placeOneFinder(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, row: Int, col: Int) {
    for (r in 0..6) {
      for (c in 0..6) {
        val isBlack = (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
        setFunctionModule(matrix, isFunc, row + r, col + c, isBlack)
      }
    }
  }

  private fun placeTimingPatterns(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, dim: Int) {
    for (i in 8 until dim - 8) {
      val isBlack = (i % 2 == 0)
      if (!isFunc[6][i]) setFunctionModule(matrix, isFunc, 6, i, isBlack)
      if (!isFunc[i][6]) setFunctionModule(matrix, isFunc, i, 6, isBlack)
    }
  }

  private fun placeAlignmentPatterns(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, version: Int, dim: Int) {
    if (version < 2) return
    val positions = getAlignmentPatternPositions(version)
    for (r in positions) {
      for (c in positions) {
        val inFinderArea = (r < 9 && c < 9) || (r < 9 && c >= dim - 9) || (r >= dim - 9 && c < 9)
        if (!inFinderArea) {
          for (dy in -2..2) {
            for (dx in -2..2) {
              val isBlack = (abs(dy) == 2 || abs(dx) == 2 || (dy == 0 && dx == 0))
              setFunctionModule(matrix, isFunc, r + dy, c + dx, isBlack)
            }
          }
        }
      }
    }
  }

  private fun placeDarkModule(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, dim: Int) {
    setFunctionModule(matrix, isFunc, dim - 8, 8, true)
  }

  private fun reserveFormatAndVersionInfo(isFunc: Array<BooleanArray>, version: Int, dim: Int) {
    // Format info around top-left, bottom-left, top-right
    for (i in 0..8) {
      isFunc[8][i] = true
      isFunc[i][8] = true
    }
    for (i in 0..7) {
      isFunc[8][dim - 1 - i] = true
      isFunc[dim - 1 - i][8] = true
    }
    // Version info for version >= 7
    if (version >= 7) {
      for (i in 0..5) {
        for (j in 0..2) {
          isFunc[dim - 11 + j][i] = true
          isFunc[i][dim - 11 + j] = true
        }
      }
    }
  }

  private fun setFunctionModule(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, r: Int, c: Int, isBlack: Boolean) {
    matrix[r][c] = isBlack
    isFunc[r][c] = true
  }

  // --- Format & Version Information Placement ---

  private fun placeFormatInfo(matrix: Array<BooleanArray>, dim: Int, mask: Int) {
    val ecFormatBits = 0 // Level M is 0b00
    val data = (ecFormatBits shl 3) or mask
    var rem = data
    for (i in 0 until 10) {
      rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
    }
    val bits = ((data shl 10) or rem) xor 0x5412

    for (i in 0 until 15) {
      val bit = ((bits ushr i) and 1) != 0

      // Top-left
      val r1 = when {
        i <= 5 -> i
        i == 6 -> 7
        i == 7 -> 8
        i == 8 -> 8
        else -> 8
      }
      val c1 = when {
        i <= 5 -> 8
        i == 6 -> 8
        i == 7 -> 8
        i == 8 -> 7
        else -> 14 - i
      }
      matrix[r1][c1] = bit

      // Top-right and bottom-left duplicates
      if (i < 7) {
        matrix[dim - 1 - i][8] = bit
      } else {
        matrix[8][dim - 15 + i] = bit
      }
    }
  }

  private fun placeVersionInfo(matrix: Array<BooleanArray>, dim: Int, version: Int) {
    var rem = version
    for (i in 0 until 12) {
      rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
    }
    val bits = (version shl 12) or rem
    for (i in 0 until 18) {
      val bit = ((bits ushr i) and 1) != 0
      val a = dim - 11 + (i % 3)
      val b = i / 3
      matrix[a][b] = bit
      matrix[b][a] = bit
    }
  }

  // --- Data Bits Placement ---

  private fun placeDataBits(
    matrix: Array<BooleanArray>,
    isFunc: Array<BooleanArray>,
    data: IntArray,
    dim: Int
  ) {
    var bitIdx = 0
    val totalBits = data.size * 8
    var upwards = true
    var c = dim - 1

    while (c > 0) {
      if (c == 6) c-- // Skip vertical timing pattern at column 6
      val rows = if (upwards) (dim - 1 downTo 0) else (0 until dim)
      for (r in rows) {
        for (colOffset in 0..1) {
          val currentCol = c - colOffset
          if (!isFunc[r][currentCol]) {
            val bit = if (bitIdx < totalBits) {
              val byteVal = data[bitIdx / 8]
              ((byteVal ushr (7 - (bitIdx % 8))) and 1) == 1
            } else {
              false
            }
            matrix[r][currentCol] = bit
            bitIdx++
          }
        }
      }
      upwards = !upwards
      c -= 2
    }
  }

  // --- Masking & Penalty Calculation ---

  private fun maskPattern(mask: Int, r: Int, c: Int): Boolean = when (mask) {
    0 -> (r + c) % 2 == 0
    1 -> r % 2 == 0
    2 -> c % 3 == 0
    3 -> (r + c) % 3 == 0
    4 -> (r / 2 + c / 3) % 2 == 0
    5 -> ((r * c) % 2 + (r * c) % 3) == 0
    6 -> (((r * c) % 2 + (r * c) % 3) % 2) == 0
    7 -> (((r + c) % 2 + (r * c) % 3) % 2) == 0
    else -> false
  }

  private fun calculatePenalty(matrix: Array<BooleanArray>, dim: Int): Int {
    var penalty = 0

    // Condition 1: 5 or more consecutive modules of the same color
    for (r in 0 until dim) {
      var count = 0
      var lastColor = false
      for (c in 0 until dim) {
        val color = matrix[r][c]
        if (c == 0 || color != lastColor) {
          lastColor = color
          count = 1
        } else {
          count++
          if (count == 5) penalty += 3
          else if (count > 5) penalty += 1
        }
      }
    }
    for (c in 0 until dim) {
      var count = 0
      var lastColor = false
      for (r in 0 until dim) {
        val color = matrix[r][c]
        if (r == 0 || color != lastColor) {
          lastColor = color
          count = 1
        } else {
          count++
          if (count == 5) penalty += 3
          else if (count > 5) penalty += 1
        }
      }
    }

    // Condition 2: 2x2 blocks of same color
    for (r in 0 until dim - 1) {
      for (c in 0 until dim - 1) {
        val color = matrix[r][c]
        if (color == matrix[r + 1][c] && color == matrix[r][c + 1] && color == matrix[r + 1][c + 1]) {
          penalty += 3
        }
      }
    }

    // Condition 3: 1:1:3:1:1 pattern with 4 white modules on either side
    for (r in 0 until dim) {
      for (c in 0 until dim - 6) {
        if (matrix[r][c] && !matrix[r][c + 1] && matrix[r][c + 2] && matrix[r][c + 3] &&
          matrix[r][c + 4] && !matrix[r][c + 5] && matrix[r][c + 6]
        ) {
          // Check 4 white before or after
          val whiteBefore = (c >= 4 && (0 until 4).all { !matrix[r][c - 1 - it] })
          val whiteAfter = (c + 10 < dim && (0 until 4).all { !matrix[r][c + 7 + it] })
          if (whiteBefore || whiteAfter) penalty += 40
        }
      }
    }
    for (c in 0 until dim) {
      for (r in 0 until dim - 6) {
        if (matrix[r][c] && !matrix[r + 1][c] && matrix[r + 2][c] && matrix[r + 3][c] &&
          matrix[r + 4][c] && !matrix[r + 5][c] && matrix[r + 6][c]
        ) {
          val whiteBefore = (r >= 4 && (0 until 4).all { !matrix[r - 1 - it][c] })
          val whiteAfter = (r + 10 < dim && (0 until 4).all { !matrix[r + 7 + it][c] })
          if (whiteBefore || whiteAfter) penalty += 40
        }
      }
    }

    // Condition 4: Proportion of dark modules
    var darkCount = 0
    for (r in 0 until dim) {
      for (c in 0 until dim) {
        if (matrix[r][c]) darkCount++
      }
    }
    val totalModules = dim * dim
    val percentDark = (darkCount * 100) / totalModules
    val prevMultipleOf5 = percentDark / 5 * 5
    val nextMultipleOf5 = prevMultipleOf5 + 5
    val k = min(abs(prevMultipleOf5 - 50), abs(nextMultipleOf5 - 50)) / 5
    penalty += k * 10

    return penalty
  }

  // --- Alignment Pattern Coordinates ---

  private fun getAlignmentPatternPositions(version: Int): IntArray {
    if (version == 1) return intArrayOf()
    val numAlign = version / 7 + 2
    val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
    val result = IntArray(numAlign)
    result[0] = 6
    var pos = version * 4 + 10
    for (i in numAlign - 1 downTo 1) {
      result[i] = pos
      pos -= step
    }
    return result
  }

  // --- Reed-Solomon Codec ---

  private val EXP_TABLE = IntArray(512)
  private val LOG_TABLE = IntArray(256)

  init {
    var x = 1
    for (i in 0 until 255) {
      EXP_TABLE[i] = x
      EXP_TABLE[i + 255] = x
      LOG_TABLE[x] = i
      x = (x shl 1) xor (if (x >= 128) 0x11D else 0)
    }
  }

  private fun gfMultiply(x: Int, y: Int): Int {
    if (x == 0 || y == 0) return 0
    return EXP_TABLE[LOG_TABLE[x] + LOG_TABLE[y]]
  }

  private fun reedSolomonGeneratorPolynomial(degree: Int): IntArray {
    var result = intArrayOf(1)
    for (i in 0 until degree) {
      val next = IntArray(result.size + 1)
      val root = EXP_TABLE[i]
      for (j in result.indices) {
        next[j] = next[j] xor result[j]
        next[j + 1] = next[j + 1] xor gfMultiply(result[j], root)
      }
      result = next
    }
    return result
  }

  private fun reedSolomonComputeRemainder(data: IntArray, generator: IntArray): IntArray {
    val ecLen = generator.size - 1
    val result = IntArray(ecLen)
    for (b in data) {
      val factor = b xor result[0]
      System.arraycopy(result, 1, result, 0, ecLen - 1)
      result[ecLen - 1] = 0
      if (factor != 0) {
        for (i in 1 until generator.size) {
          result[i - 1] = result[i - 1] xor gfMultiply(generator[i], factor)
        }
      }
    }
    return result
  }

  // --- Helpers ---

  private fun appendBits(buffer: MutableList<Int>, value: Int, count: Int) {
    for (i in count - 1 downTo 0) {
      buffer.add((value ushr i) and 1)
    }
  }

  private fun selectVersion(numBytes: Int): Int {
    for (v in 1..40) {
      val totalCodewords = NUM_RAW_DATA_MODULES[v] / 8
      val ecCodewords = ECC_BLOCKS_LEVEL_M[v] * ECC_CODEWORDS_PER_BLOCK_LEVEL_M[v]
      val dataCodewords = totalCodewords - ecCodewords
      val headerBits = 4 + (if (v <= 9) 8 else 16)
      val maxDataBytes = (dataCodewords * 8 - headerBits) / 8
      if (numBytes <= maxDataBytes) return v
    }
    throw IllegalArgumentException("Data string too large to encode in QR Code (bytes=$numBytes)")
  }

  // --- Standard Specification Tables ---

  private val ECC_BLOCKS_LEVEL_M = intArrayOf(
    0,
    1, 1, 1, 2, 2, 4, 4, 4, 5, 5,
    5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
    17, 17, 18, 20, 21, 23, 25, 26, 28, 29,
    31, 33, 35, 37, 38, 40, 43, 45, 47, 49
  )

  private val ECC_CODEWORDS_PER_BLOCK_LEVEL_M = intArrayOf(
    0,
    10, 16, 26, 18, 24, 16, 18, 22, 22, 26,
    30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
    26, 28, 28, 28, 28, 28, 28, 28, 28, 28,
    28, 28, 28, 28, 28, 28, 28, 28, 28, 28
  )

  private val NUM_RAW_DATA_MODULES = intArrayOf(
    0,
    208, 359, 567, 807, 1079, 1383, 1568, 1936, 2336, 2768,
    3232, 3728, 4256, 4651, 5243, 5867, 6523, 7211, 7931, 8683,
    9252, 10068, 10916, 11796, 12708, 13652, 14628, 15371, 16411, 17483,
    18587, 19723, 20891, 22091, 23008, 24272, 25568, 26896, 28256, 29648
  )
}
