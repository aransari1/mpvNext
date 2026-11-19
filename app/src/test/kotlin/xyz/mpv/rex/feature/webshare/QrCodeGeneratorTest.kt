package xyz.mpv.rex.feature.webshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {

  @Test
  fun testGenerateMatrix_version1_quietZoneAndDimensions() {
    val matrix = QrCodeGenerator.generateMatrix("HELLO")
    // Version 1 is 21x21 modules + 4 quiet zone on all sides = 29x29
    assertEquals(29, matrix.size)
    assertEquals(29, matrix[0].size)

    // Check quiet zone around borders (first 4 and last 4 rows and cols should be all false)
    for (r in 0 until 4) {
      for (c in 0 until 29) {
        assertFalse("Quiet zone top should be white", matrix[r][c])
        assertFalse("Quiet zone bottom should be white", matrix[28 - r][c])
      }
    }
    for (c in 0 until 4) {
      for (r in 0 until 29) {
        assertFalse("Quiet zone left should be white", matrix[r][c])
        assertFalse("Quiet zone right should be white", matrix[r][28 - c])
      }
    }
  }

  @Test
  fun testGenerateMatrix_typicalWebShareUrl() {
    val url = "http://192.168.1.100:8080/?t=123456"
    val matrix = QrCodeGenerator.generateMatrix(url)
    assertNotNull(matrix)
    assertTrue("Matrix dimension should be > 29", matrix.size > 29)

    val qz = 4
    val dim = matrix.size - qz * 2

    // Check top-left finder center (at qz + 3, qz + 3)
    assertTrue("Top-left finder center should be black", matrix[qz + 3][qz + 3])
    // Check top-right finder center (at qz + 3, qz + dim - 4)
    assertTrue("Top-right finder center should be black", matrix[qz + 3][qz + dim - 4])
    // Check bottom-left finder center (at qz + dim - 4, qz + 3)
    assertTrue("Bottom-left finder center should be black", matrix[qz + dim - 4][qz + 3])

    // Check dark module at (qz + dim - 8, qz + 8)
    assertTrue("Dark module should always be black", matrix[qz + dim - 8][qz + 8])
  }

  @Test
  fun testGenerateMatrix_timingPatterns() {
    val matrix = QrCodeGenerator.generateMatrix("TEST")
    val qz = 4
    val dim = matrix.size - qz * 2

    // Check timing patterns between finder patterns (row 6 and col 6)
    for (i in 8 until dim - 8) {
      val expected = (i % 2 == 0)
      assertEquals("Horizontal timing pattern at $i", expected, matrix[qz + 6][qz + i])
      assertEquals("Vertical timing pattern at $i", expected, matrix[qz + i][qz + 6])
    }
  }
}
