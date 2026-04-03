package world.gregs.voidps.client

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.MutableColor
import world.gregs.voidps.buffer.BufferReader
import kotlin.math.pow
import kotlin.random.Random

class MeshDecoder {

    init {
        initHSVToRGB()
        initHSLToRGB()
    }

    private var anInt1828 = 0
    private var vertexCount = 0
    private var faceCount = 0
    private var texSpaceCount = 0
    private var version = 0
    private var maxVertex = 0

    // Vertex data
    private lateinit var vertexX: IntArray
    private lateinit var vertexY: IntArray
    private lateinit var vertexZ: IntArray
    private var vertexLabel: IntArray? = null

    // Face data
    private lateinit var faceA: ShortArray
    private lateinit var faceB: ShortArray
    private lateinit var faceC: ShortArray
    private lateinit var faceColour: ShortArray
    private var faceLabel: IntArray? = null
    private var shadingType: ByteArray? = null
    private var faceAlpha: ByteArray? = null
    private var facePriority: ByteArray? = null
    private var globalPriority: Byte = 0
    private var faceTexture: ShortArray? = null
    private var faceTexSpace: ByteArray? = null

    // Texture mapping data
    private var texMappingType: ByteArray? = null
    private var texSpaceDefA: ShortArray? = null
    private var texSpaceDefB: ShortArray? = null
    private var texSpaceDefC: ShortArray? = null
    private var texOffsetX: IntArray? = null
    private var texOffsetY: IntArray? = null
    private var texOffsetZ: IntArray? = null
    private var texSpaceScaleX: IntArray? = null
    private var texSpaceScaleY: IntArray? = null
    private var texSpaceScaleZ: IntArray? = null
    private var texRotation: ByteArray? = null
    private var texDirection: ByteArray? = null

    // Particle and billboard data
    private var emitters: Array<ModelParticleEmitter>? = null
    private var effectors: Array<ModelParticleEffector>? = null
    private var billboards: Array<MeshBillboard>? = null

    fun decodeToModel(meshBuilder: MeshBuilder, data: ByteArray) {
        if (data[data.size - 1].toInt() == -1 && data[data.size - 2].toInt() == -1) {
            decodeNew(data)
        } else {
            println("Old model")
        }
        createKoolModel(meshBuilder)
    }

    private fun decodeNew(data: ByteArray) {
        anInt1828++

        val packet = BufferReader(data)
        val packet2 = BufferReader(data)
        val packet3 = BufferReader(data)
        val packet4 = BufferReader(data)
        val packet5 = BufferReader(data)
        val packet6 = BufferReader(data)
        val packet7 = BufferReader(data)

        packet.position = data.size - 23
        vertexCount = packet.readUnsignedShort()
        faceCount = packet.readUnsignedShort()
        texSpaceCount = packet.readUnsignedByte()

        val globalFlags = packet.readUnsignedByte()
        val hasFlatShading = (globalFlags and 0x1) == 1
        val hasParticleEffects = (globalFlags and 0x2) == 2
        val hasBillboards = (globalFlags and 0x4) == 4
        val hasVersion = (globalFlags and 0x8) == 8

        if (hasVersion) {
            packet.position -= 7
            version = packet.readUnsignedByte()
            packet.position += 6
        }

        val priorityFlag = packet.readUnsignedByte()
        val faceAlphaFlag = packet.readUnsignedByte()
        val faceGroupFlag = packet.readUnsignedByte()
        val faceTextureFlag = packet.readUnsignedByte()
        val vertexLabelFlag = packet.readUnsignedByte()

        val vertexLengthX = packet.readUnsignedShort()
        val vertexLengthY = packet.readUnsignedShort()
        val vertexLengthZ = packet.readUnsignedShort()
        val faceDataSize = packet.readUnsignedShort()
        val texSpaceSize = packet.readUnsignedShort()

        var planarMappingCount = 0
        var complexMappingCount = 0
        var cubeMappingCount = 0

        if (texSpaceCount > 0) {
            packet.position = 0
            texMappingType = ByteArray(texSpaceCount)
            for (i in 0 until texSpaceCount) {
                val type = packet.readByte()
                texMappingType!![i] = type.toByte()
                if (type in 1..3) {
                    complexMappingCount++
                }
                if (type == 2) cubeMappingCount++
                if (type == 0) planarMappingCount++
            }
        }

        // Calculate pointers
        var ptr = texSpaceCount
        val vertexFlagsPtr = ptr
        ptr += vertexCount
        val smoothingPtr = ptr
        if (hasFlatShading) ptr += faceCount
        val faceTypePtr = ptr
        ptr += faceCount
        val facePriPtr = ptr
        if (priorityFlag == 255) ptr += faceCount
        val faceGroupPtr = ptr
        if (faceGroupFlag == 1) ptr += faceCount
        val vertexLabelPtr = ptr
        if (vertexLabelFlag == 1) ptr += vertexCount
        val faceAlphaPtr = ptr
        if (faceAlphaFlag == 1) ptr += faceCount
        val faceDataSizePtr = ptr
        ptr += faceDataSize
        val faceTextureFlagPtr = ptr
        if (faceTextureFlag == 1) ptr += 2 * faceCount
        val faceTextureSpacePtr = ptr
        ptr += texSpaceSize
        val faceColourPtr = ptr
        ptr += 2 * faceCount
        val vertexXPtr = ptr
        ptr += vertexLengthX
        val vertexYPtr = ptr
        ptr += vertexLengthY
        val vertexZPtr = ptr
        ptr += vertexLengthZ
        val planarMappingPtr = ptr
        ptr += planarMappingCount * 6
        val complexMappingPtr = ptr
        ptr += 6 * complexMappingCount

        val texSpaceScaleSize = when {
            version == 14 -> 7
            version >= 15 -> 9
            else -> 6
        }
        val texSpaceScalePtr = ptr
        ptr += texSpaceScaleSize * complexMappingCount
        val texSpaceRotationPtr = ptr
        ptr += complexMappingCount
        val texSpaceOrientationPtr = ptr
        ptr += complexMappingCount
        val texSpaceOffsetPtr = ptr
        ptr += 2 * cubeMappingCount + complexMappingCount

        // Initialise arrays
        faceA = ShortArray(faceCount)
        faceB = ShortArray(faceCount)
        faceC = ShortArray(faceCount)
        faceColour = ShortArray(faceCount)

        if (faceGroupFlag == 1) faceLabel = IntArray(faceCount)

        if (texSpaceCount > 0) {
            texSpaceDefA = ShortArray(texSpaceCount)
            texSpaceDefB = ShortArray(texSpaceCount)
            texSpaceDefC = ShortArray(texSpaceCount)

            if (cubeMappingCount > 0) {
                texOffsetY = IntArray(cubeMappingCount)
                texOffsetZ = IntArray(cubeMappingCount)
            }

            if (complexMappingCount > 0) {
                texOffsetX = IntArray(complexMappingCount)
                texSpaceScaleX = IntArray(complexMappingCount)
                texSpaceScaleY = IntArray(complexMappingCount)
                texSpaceScaleZ = IntArray(complexMappingCount)
                texRotation = ByteArray(complexMappingCount)
                texDirection = ByteArray(complexMappingCount)
            }
        }

        if (hasFlatShading) shadingType = ByteArray(faceCount)
        if (faceAlphaFlag == 1) faceAlpha = ByteArray(faceCount)
        if (priorityFlag == 255) facePriority = ByteArray(faceCount)
        else globalPriority = priorityFlag.toByte()

        if (faceTextureFlag == 1 && texSpaceCount > 0) faceTexSpace = ByteArray(faceCount)
        if (faceTextureFlag == 1) faceTexture = ShortArray(faceCount)

        vertexX = IntArray(vertexCount)
        vertexY = IntArray(vertexCount)
        vertexZ = IntArray(vertexCount)

        if (vertexLabelFlag == 1) vertexLabel = IntArray(vertexCount)

        // Read vertex data
        packet.position = vertexFlagsPtr
        packet2.position = vertexXPtr
        packet3.position = vertexYPtr
        packet4.position = vertexZPtr
        packet5.position = vertexLabelPtr

        var pvX = 0
        var pvY = 0
        var pvZ = 0

        for (i in 0 until vertexCount) {
            val vertexData = packet.readUnsignedByte()
            var x = 0
            if ((vertexData and 0x1) != 0) x = packet2.readSmartSub()
            var y = 0
            if ((vertexData and 0x2) != 0) y = packet3.readSmartSub()
            var z = 0
            if ((vertexData and 0x4) != 0) z = packet4.readSmartSub()

            vertexX[i] = x + pvX
            vertexY[i] = y + pvY
            vertexZ[i] = z + pvZ

            pvX = vertexX[i]
            pvY = vertexY[i]
            pvZ = vertexZ[i]

            if (vertexLabelFlag == 1) vertexLabel!![i] = packet5.readUnsignedByte()
        }

        // Read face colour and properties
        packet.position = faceColourPtr
        packet2.position = smoothingPtr
        packet3.position = facePriPtr
        packet4.position = faceAlphaPtr
        packet5.position = faceGroupPtr
        packet6.position = faceTextureFlagPtr
        packet7.position = faceTextureSpacePtr

        for (i in 0 until faceCount) {
            faceColour[i] = packet.readUnsignedShort().toShort()
            if (hasFlatShading) shadingType!![i] = packet2.readByte().toByte()
            if (priorityFlag == 255) facePriority!![i] = packet3.readByte().toByte()
            if (faceAlphaFlag == 1) faceAlpha!![i] = packet4.readByte().toByte()
            if (faceGroupFlag == 1) faceLabel!![i] = packet5.readUnsignedByte()
            if (faceTextureFlag == 1) faceTexture!![i] = (packet6.readUnsignedShort() - 1).toShort()
            if (faceTexSpace != null) {
                if (faceTexture!![i] == (-1).toShort()) {
                    faceTexSpace!![i] = -1
                } else {
                    faceTexSpace!![i] = (packet7.readUnsignedByte() - 1).toByte()
                }
            }
        }

        // Read face vertex indices
        packet.position = faceDataSizePtr
        packet2.position = faceTypePtr

        var faceA: Short = 0
        var faceB: Short = 0
        var faceC: Short = 0
        var facePriority = 0
        maxVertex = -1

        for (i in 0 until faceCount) {
            val type = packet2.readUnsignedByte()

            when (type) {
                1 -> {
                    faceA = (packet.readSmartSub() + facePriority).toShort()
                    facePriority = faceA.toInt()
                    faceB = (packet.readSmartSub() + facePriority).toShort()
                    facePriority = faceB.toInt()
                    faceC = (packet.readSmartSub() + facePriority).toShort()
                    facePriority = faceC.toInt()
                    this.faceA[i] = faceA
                    this.faceB[i] = faceB
                    this.faceC[i] = faceC

                    if (this.maxVertex < faceA) {
                        this.maxVertex = faceA.toInt()
                    }

                    if (this.maxVertex < faceB) {
                        this.maxVertex = faceB.toInt()
                    }

                    if (this.maxVertex < faceC) {
                        this.maxVertex = faceC.toInt()
                    }
                }
                2 -> {
                    faceB = faceC
                    faceC = (packet.readSmartSub() + facePriority).toShort()
                    facePriority = faceC.toInt()
                    this.faceA[i] = faceA
                    this.faceB[i] = faceB
                    this.faceC[i] = faceC
                    if (this.maxVertex < faceC) {
                        this.maxVertex = faceC.toInt()
                    }
                }
                3 -> {
                    faceA = faceC
                    faceC = (packet.readSmartSub() + facePriority).toShort()
                    facePriority = faceC.toInt()
                    this.faceA[i] = faceA
                    this.faceB[i] = faceB
                    this.faceC[i] = faceC
                    if (this.maxVertex < faceC) {
                        this.maxVertex = faceC.toInt()
                    }
                }
                4 -> {
                    val temp = faceA
                    faceA = faceB
                    faceB = temp
                    faceC = (packet.readSmartSub() + facePriority).toShort()
                    facePriority = faceC.toInt()
                    this.faceA[i] = faceA
                    this.faceB[i] = faceB
                    this.faceC[i] = faceC
                    if (this.maxVertex < faceC) {
                        this.maxVertex = faceC.toInt()
                    }
                }
            }
        }

        maxVertex++

        // Read texture mapping data (simplified - keeping structure but not fully implementing)
        packet.position = planarMappingPtr
        packet2.position = complexMappingPtr
        packet3.position = texSpaceScalePtr
        packet4.position = texSpaceRotationPtr
        packet5.position = texSpaceOrientationPtr
        packet6.position = texSpaceOffsetPtr

        for (i in 0 until texSpaceCount) {
            val type = texMappingType!![i].toInt() and 0xff
            if (type == 0) {
                this.texSpaceDefA!![i] = packet.readUnsignedShort().toShort()
                this.texSpaceDefB!![i] = packet.readUnsignedShort().toShort()
                this.texSpaceDefC!![i] = packet.readUnsignedShort().toShort()
            }
            if (type == 1) {
                this.texSpaceDefA!![i] = packet2.readUnsignedShort().toShort()
                this.texSpaceDefB!![i] = packet2.readUnsignedShort().toShort()
                this.texSpaceDefC!![i] = packet2.readUnsignedShort().toShort()
                if (this.version >= 15) {
                    this.texSpaceScaleX!![i] = packet3.readMedium()
                    this.texSpaceScaleY!![i] = packet3.readMedium()
                    this.texSpaceScaleZ!![i] = packet3.readMedium()
                } else {
                    this.texSpaceScaleX!![i] = packet3.readUnsignedShort()
                    if (this.version >= 14) this.texSpaceScaleY!![i] = packet3.readMedium()
                    else this.texSpaceScaleY!![i] = packet3.readUnsignedShort()
                    this.texSpaceScaleZ!![i] = packet3.readUnsignedShort()
                }
                this.texRotation!![i] = packet4.readByte().toByte()
                this.texDirection!![i] = packet5.readByte().toByte()
                this.texOffsetX!![i] = packet6.readByte()
            }
            if (type == 2) {
                this.texSpaceDefA!![i] = packet2.readUnsignedShort().toShort()
                this.texSpaceDefB!![i] = packet2.readUnsignedShort().toShort()
                this.texSpaceDefC!![i] = packet2.readUnsignedShort().toShort()
                if (this.version >= 15) {
                    this.texSpaceScaleX!![i] = packet3.readMedium()
                    this.texSpaceScaleY!![i] = packet3.readMedium()
                    this.texSpaceScaleZ!![i] = packet3.readMedium()
                } else {
                    this.texSpaceScaleX!![i] = packet3.readUnsignedShort()
                    if (this.version < 14) this.texSpaceScaleY!![i] = packet3.readUnsignedShort()
                    else this.texSpaceScaleY!![i] = packet3.readMedium()
                    this.texSpaceScaleZ!![i] = packet3.readUnsignedShort()
                }
                this.texRotation!![i] = packet4.readByte().toByte()
                this.texDirection!![i] = packet5.readByte().toByte()
                this.texOffsetX!![i] = packet6.readByte()
                this.texOffsetY!![i] = packet6.readByte()
                this.texOffsetZ!![i] = packet6.readByte()
            }
            if (type == 3) {
                this.texSpaceDefA!![i] = packet2.readUnsignedShort().toShort()
                this.texSpaceDefB!![i] = packet2.readUnsignedShort().toShort()
                this.texSpaceDefC!![i] = packet2.readUnsignedShort().toShort()
                if (this.version < 15) {
                    this.texSpaceScaleX!![i] = packet3.readUnsignedShort()
                    if (this.version < 14) this.texSpaceScaleY!![i] = packet3.readUnsignedShort()
                    else this.texSpaceScaleY!![i] = packet3.readMedium()
                    this.texSpaceScaleZ!![i] = packet3.readUnsignedShort()
                } else {
                    this.texSpaceScaleX!![i] = packet3.readMedium()
                    this.texSpaceScaleY!![i] = packet3.readMedium()
                    this.texSpaceScaleZ!![i] = packet3.readMedium()
                }
                this.texRotation!![i] = packet4.readByte().toByte()
                this.texDirection!![i] = packet5.readByte().toByte()
                this.texOffsetX!![i] = packet6.readByte()
            }
        }

        // Read particle effects and billboards
        val particlePtr = ptr
        packet.position = particlePtr

        if (hasParticleEffects) {
            val emitterCount = packet.readUnsignedByte()
            if (emitterCount > 0) {
                emitters = Array(emitterCount) {
                    val type = packet.readUnsignedShort()
                    val face = packet.readUnsignedShort()
                    val priority = if (priorityFlag != 255) priorityFlag.toByte() else this.facePriority!![face]
                    ModelParticleEmitter(type, this.faceA[face], this.faceB[face], this.faceC[face], priority)
                }
            }

            val effectorCount = packet.readUnsignedByte()
            if (effectorCount > 0) {
                effectors = Array(effectorCount) {
                    ModelParticleEffector(packet.readUnsignedShort(), packet.readUnsignedShort())
                }
            }
        }

        if (hasBillboards) {
            val billboardCount = packet.readUnsignedByte()
            if (billboardCount > 0) {
                billboards = Array(billboardCount) {
                    MeshBillboard(
                        packet.readUnsignedShort(),
                        packet.readUnsignedShort(),
                        packet.readUnsignedByte(),
                        packet.readByte().toByte()
                    )
                }
            }
        }
    }

    private fun createKoolModel(meshBuilder: MeshBuilder) {
        with(meshBuilder) {
            val tmpColor = MutableColor()
            // Add triangles using the stored vertex indices
            var invalidIndices = 0
            for (i in 0 until faceCount) {
                val a = faceA[i].toInt()
                val b = faceB[i].toInt()
                val c = faceC[i].toInt()

                val colorValue = HSV_TO_RGB[faceColour[i].toInt()]
                val red = ((colorValue shr 16) and 0xfF).toFloat() / 255f
                val green = ((colorValue shr 8) and 0xfF).toFloat() / 255f
                val blue = (colorValue and 0xfF).toFloat() / 255f

                tmpColor.set(red, green, blue, 1f)

                // Skip degenerate triangles
                if (a >= 0 && b >= 0 && c >= 0 &&
                    a < vertexCount && b < vertexCount && c < vertexCount &&
                    a != b && b != c && a != c
                ) {

                    withColor(tmpColor) {
                        // Rotate 180° around X axis: Y -> -Y, Z -> -Z
                        val v0 = MutableVec3f(vertexX[a].toFloat(), -vertexY[a].toFloat(), -vertexZ[a].toFloat())
                        val v1 = MutableVec3f(vertexX[b].toFloat(), -vertexY[b].toFloat(), -vertexZ[b].toFloat())
                        val v2 = MutableVec3f(vertexX[c].toFloat(), -vertexY[c].toFloat(), -vertexZ[c].toFloat())

                        val edge1 = v1 - v0
                        val edge2 = v2 - v0
                        val normal = edge1.cross(edge2, MutableVec3f()).norm()


                        val i0 = vertex {
                            this.normal.set(normal)
                            position.set(v0)
                            color.set(tmpColor)
                        }

                        val i1 = vertex {
                            this.normal.set(normal)
                            position.set(v1)
                            color.set(tmpColor)
                        }

                        val i2 = vertex {
                            this.normal.set(normal)
                            position.set(v2)
                            color.set(tmpColor)
                        }
                        // Add triangle using the vertex indices from our array
                        addTriIndices(i0, i1, i2)
                    }
                } else {
                    invalidIndices++
                    if (invalidIndices < 10) {
                        println("Invalid indices at face $i: [$a, $b, $c], vertexCount=$vertexCount")
                    }
                }
            }
        }
    }


    lateinit var HSV_TO_RGB: IntArray
    lateinit var HSL_TO_RGB: IntArray

    fun initHSLToRGB() {
        HSL_TO_RGB = IntArray(65536)
        val d = 0.7 + (0.03 * Random.nextDouble() - 0.015)
        for (i_5_ in 0 until 65536) {
            val d_6_ = 0.0078125 + ((0xfebd and i_5_) shr 10).toDouble() / 64.0
            val d_7_ = ((0x384 and i_5_) shr 7).toDouble() / 8.0 + 0.0625
            val d_8_ = (i_5_ and 0x7f).toDouble() / 128.0
            var d_9_ = d_8_
            var d_10_ = d_8_
            var d_11_ = d_8_
            if (d_7_ != 0.0) {
                val d_12_ = if (d_8_ < 0.5) d_8_ * (d_7_ + 1.0)
                else -(d_8_ * d_7_) + (d_8_ + d_7_)
                val d_13_ = 2.0 * d_8_ - d_12_
                var d_14_ = 0.3333333333333333 + d_6_
                if (d_14_ > 1.0) d_14_--
                val d_15_ = d_6_
                var d_16_ = -0.3333333333333333 + d_6_
                if (d_16_ < 0.0) d_16_++
                d_9_ = if (6.0 * d_14_ < 1.0) d_13_ + 6.0 * (-d_13_ + d_12_) * d_14_
                else if (!(2.0 * d_14_ < 1.0)) {
                    if (!(3.0 * d_14_ < 2.0)) d_13_
                    else 6.0 * ((d_12_ - d_13_) * (-d_14_ + 0.6666666666666666)) + d_13_
                } else d_12_
                d_10_ = if (!(6.0 * d_15_ < 1.0)) {
                    if (!(2.0 * d_15_ < 1.0)) {
                        if (!(d_15_ * 3.0 < 2.0)) d_13_
                        else ((-d_15_ + 0.6666666666666666) * (d_12_ - d_13_) * 6.0) + d_13_
                    } else d_12_
                } else d_13_ + d_15_ * (6.0 * (d_12_ - d_13_))
                d_11_ = if (d_16_ * 6.0 < 1.0) d_13_ + (d_12_ - d_13_) * 6.0 * d_16_
                else if (!(2.0 * d_16_ < 1.0)) {
                    if (3.0 * d_16_ < 2.0) d_13_ + ((-d_13_ + d_12_) * (0.6666666666666666 - d_16_) * 6.0)
                    else d_13_
                } else d_12_
            }
            d_9_ = d_9_.pow(d)
            d_10_ = d_10_.pow(d)
            d_11_ = d_11_.pow(d)
            val i_17_ = (d_9_ * 256.0).toInt()
            val i_18_ = (d_10_ * 256.0).toInt()
            val i_19_ = (d_11_ * 256.0).toInt()
            val i_20_ = (i_18_ shl 8) + (i_17_ shl 16) - -i_19_
            HSL_TO_RGB[i_5_] = i_20_
        }
    }

    fun initHSVToRGB() {
        HSV_TO_RGB = IntArray(65536)
        val d = 0.7 + (0.03 * Random.nextDouble() - 0.015)
        var i_5_ = 0
        for (i_6_ in 0 until 512) {
            val f = (((i_6_ shr 3).toFloat() / 64.0f + 0.0078125f) * 360.0f)
            val f_7_ = (i_6_ and 0x7).toFloat() / 8.0f + 0.0625f
            for (i_8_ in 0 until 128) {
                val f_9_ = i_8_.toFloat() / 128.0f
                var f_10_ = 0.0f
                var f_11_ = 0.0f
                var f_12_ = 0.0f
                val f_13_ = f / 60.0f
                val i_14_ = f_13_.toInt()
                val i_15_ = i_14_ % 6
                val f_16_ = -i_14_.toFloat() + f_13_
                val f_17_ = f_9_ * (-f_7_ + 1.0f)
                val f_18_ = f_9_ * (1.0f - f_16_ * f_7_)
                val f_19_ = (1.0f - (1.0f - f_16_) * f_7_) * f_9_
                if (i_15_ == 0) {
                    f_10_ = f_9_
                    f_11_ = f_19_
                    f_12_ = f_17_
                } else if (i_15_ == 1) {
                    f_11_ = f_9_
                    f_10_ = f_18_
                    f_12_ = f_17_
                } else if (i_15_ == 2) {
                    f_12_ = f_19_
                    f_10_ = f_17_
                    f_11_ = f_9_
                } else if (i_15_ == 3) {
                    f_12_ = f_9_
                    f_11_ = f_18_
                    f_10_ = f_17_
                } else if (i_15_ == 4) {
                    f_12_ = f_9_
                    f_11_ = f_17_
                    f_10_ = f_19_
                } else if (i_15_ == 5) {
                    f_10_ = f_9_
                    f_11_ = f_17_
                    f_12_ = f_18_
                }
                f_10_ = f_10_.toDouble().pow(d).toFloat()
                f_11_ = f_11_.toDouble().pow(d).toFloat()
                f_12_ = f_12_.toDouble().pow(d).toFloat()
                val i_20_ = (f_10_ * 256.0f).toInt()
                val i_21_ = (256.0f * f_11_).toInt()
                val i_22_ = (256.0f * f_12_).toInt()
                val i_23_ = ((i_21_ shl 8) + ((i_20_ shl 16) + (-16777216 + i_22_)))
                HSV_TO_RGB[i_5_++] = i_23_
            }
        }
    }

}

data class ModelParticleEmitter(
    val type: Int,
    val faceA: Short,
    val faceB: Short,
    val faceC: Short,
    val priority: Byte,
)

data class ModelParticleEffector(val param1: Int, val param2: Int)
data class MeshBillboard(val type: Int, val face: Int, val group: Int, val priority: Byte)
