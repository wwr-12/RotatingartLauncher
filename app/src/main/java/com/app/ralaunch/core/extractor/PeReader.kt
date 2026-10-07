package com.app.ralaunch.core.extractor

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * PE (Portable Executable) 文件读取器
 * 用于解析 Windows EXE/DLL 文件格式
 */
class PeReader(private val file: RandomAccessFile) {

    /**
     * 检查是否为有效的 PE 文件
     */
    @Throws(IOException::class)
    fun isPeFormat(): Boolean {
        file.seek(0)
        val mzSignature = ByteArray(2)
        file.read(mzSignature)
        return mzSignature[0] == 'M'.code.toByte() && mzSignature[1] == 'Z'.code.toByte()
    }

    /**
     * 读取 PE 文件头
     */
    @Throws(IOException::class)
    fun readPeHeader(): PeHeader {
        file.seek(0)

        // 读取 MZ Header
        val mzHeader = ByteArray(64)
        file.read(mzHeader)
        val mzBuffer = ByteBuffer.wrap(mzHeader).order(ByteOrder.LITTLE_ENDIAN)

        // e_lfanew 在偏移 0x3C
        val peOffset = mzBuffer.getInt(0x3C)

        // 跳转到 PE Header
        file.seek(peOffset.toLong())
        val peSignature = ByteArray(4)
        file.read(peSignature)

        // 验证 PE 签名 "PE\0\0"
        if (peSignature[0] != 'P'.code.toByte() || peSignature[1] != 'E'.code.toByte() ||
            peSignature[2].toInt() != 0 || peSignature[3].toInt() != 0
        ) {
            throw IOException("Invalid PE signature")
        }

        // 读取 COFF Header (20 bytes)
        val coffHeader = ByteArray(20)
        file.read(coffHeader)
        val coffBuffer = ByteBuffer.wrap(coffHeader).order(ByteOrder.LITTLE_ENDIAN)

        val numberOfSections = coffBuffer.getShort(2).toInt() and 0xFFFF
        val sizeOfOptionalHeader = coffBuffer.getShort(16).toInt() and 0xFFFF

        // 读取 Optional Header
        val optionalHeaderOffset = file.filePointer

        return PeHeader(
            peOffset = peOffset,
            numberOfSections = numberOfSections,
            optionalHeaderOffset = optionalHeaderOffset,
            sizeOfOptionalHeader = sizeOfOptionalHeader
        )
    }

    /**
     * 读取资源目录表
     */
    @Throws(IOException::class)
    fun readResourceSection(peHeader: PeHeader): ResourceSection? {
        file.seek(peHeader.optionalHeaderOffset)

        // 读取 Optional Header 的前几个字节
        val optHeader = ByteArray(min(peHeader.sizeOfOptionalHeader, 256))
        file.read(optHeader)
        val optBuffer = ByteBuffer.wrap(optHeader).order(ByteOrder.LITTLE_ENDIAN)

        // 获取 Magic (PE32 = 0x10B, PE32+ = 0x20B)
        val magic = optBuffer.getShort(0).toInt() and 0xFFFF
        val is64Bit = (magic == 0x20B)

        // DataDirectory 偏移
        val dataDirectoryOffset = if (is64Bit) 112 else 96

        // 第3个 DataDirectory 是资源表 (索引2，每个8字节)
        val resourceTableOffset = dataDirectoryOffset + (2 * 8)
        val resourceRva = optBuffer.getInt(resourceTableOffset)
        val resourceSize = optBuffer.getInt(resourceTableOffset + 4)

        if (resourceRva == 0 || resourceSize == 0) {
            return null
        }

        // 读取 Section Headers
        val sectionHeaderOffset = peHeader.optionalHeaderOffset + peHeader.sizeOfOptionalHeader
        file.seek(sectionHeaderOffset)

        var resourceSectionHeader: SectionHeader? = null
        for (i in 0 until peHeader.numberOfSections) {
            val sectionData = ByteArray(40)
            file.read(sectionData)
            val sectionBuffer = ByteBuffer.wrap(sectionData).order(ByteOrder.LITTLE_ENDIAN)

            val virtualAddress = sectionBuffer.getInt(12)
            val virtualSize = sectionBuffer.getInt(8)
            val rawDataPointer = sectionBuffer.getInt(20)

            // 检查资源 RVA 是否在这个 Section 中
            if (resourceRva >= virtualAddress && resourceRva < virtualAddress + virtualSize) {
                resourceSectionHeader = SectionHeader(
                    virtualAddress = virtualAddress,
                    rawDataPointer = rawDataPointer
                )
                break
            }
        }

        if (resourceSectionHeader == null) {
            return null
        }

        // 计算资源表在文件中的实际偏移
        val resourceFileOffset = resourceSectionHeader.rawDataPointer.toLong() +
                (resourceRva - resourceSectionHeader.virtualAddress)

        return ResourceSection(
            sectionHeader = resourceSectionHeader,
            resourceRva = resourceRva,
            resourceFileOffset = resourceFileOffset
        )
    }

    /**
     * 读取资源目录
     */
    @Throws(IOException::class)
    fun readResourceDirectory(resourceSection: ResourceSection, offset: Long): ResourceDirectory {
        file.seek(offset)

        val dirHeader = ByteArray(16)
        file.read(dirHeader)
        val buffer = ByteBuffer.wrap(dirHeader).order(ByteOrder.LITTLE_ENDIAN)

        val characteristics = buffer.getInt(0)
        val numberOfNamedEntries = buffer.getShort(12).toInt() and 0xFFFF
        val numberOfIdEntries = buffer.getShort(14).toInt() and 0xFFFF

        val totalEntries = numberOfNamedEntries + numberOfIdEntries
        val entries = Array(totalEntries) { ResourceDirectoryEntry(0, 0, false, 0) }

        // 读取所有条目
        for (i in 0 until totalEntries) {
            val entryData = ByteArray(8)
            file.read(entryData)
            val entryBuffer = ByteBuffer.wrap(entryData).order(ByteOrder.LITTLE_ENDIAN)

            val nameOrId = entryBuffer.getInt(0)
            val offsetToData = entryBuffer.getInt(4)
            val isDirectory = (offsetToData and 0x80000000.toInt()) != 0
            val entryOffset = offsetToData and 0x7FFFFFFF

            entries[i] = ResourceDirectoryEntry(
                nameOrId = nameOrId,
                offsetToData = offsetToData,
                isDirectory = isDirectory,
                offset = entryOffset
            )
        }

        return ResourceDirectory(
            characteristics = characteristics,
            numberOfNamedEntries = numberOfNamedEntries,
            numberOfIdEntries = numberOfIdEntries,
            entries = entries
        )
    }

    /**
     * 读取资源数据
     */
    @Throws(IOException::class)
    fun readResourceData(resourceSection: ResourceSection, dataEntryOffset: Long): ByteArray {
        file.seek(dataEntryOffset)

        val dataEntry = ByteArray(16)
        file.read(dataEntry)
        val buffer = ByteBuffer.wrap(dataEntry).order(ByteOrder.LITTLE_ENDIAN)

        val dataRva = buffer.getInt(0)
        val size = buffer.getInt(4)

        // RVA 转换为文件偏移
        val fileOffset = resourceSection.sectionHeader.rawDataPointer.toLong() +
                (dataRva - resourceSection.sectionHeader.virtualAddress)

        file.seek(fileOffset)
        val data = ByteArray(size)
        file.read(data)

        return data
    }

    /**
     * PE 文件头信息
     */
    data class PeHeader(
        val peOffset: Int,
        val numberOfSections: Int,
        val optionalHeaderOffset: Long,
        val sizeOfOptionalHeader: Int
    )

    /**
     * Section Header
     */
    data class SectionHeader(
        val virtualAddress: Int,
        val rawDataPointer: Int
    )

    /**
     * 资源 Section
     */
    data class ResourceSection(
        val sectionHeader: SectionHeader,
        val resourceRva: Int,
        val resourceFileOffset: Long
    )

    /**
     * 资源目录
     */
    data class ResourceDirectory(
        val characteristics: Int,
        val numberOfNamedEntries: Int,
        val numberOfIdEntries: Int,
        val entries: Array<ResourceDirectoryEntry>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ResourceDirectory
            return characteristics == other.characteristics &&
                    numberOfNamedEntries == other.numberOfNamedEntries &&
                    numberOfIdEntries == other.numberOfIdEntries &&
                    entries.contentEquals(other.entries)
        }

        override fun hashCode(): Int {
            var result = characteristics
            result = 31 * result + numberOfNamedEntries
            result = 31 * result + numberOfIdEntries
            result = 31 * result + entries.contentHashCode()
            return result
        }
    }

    /**
     * 资源目录条目
     */
    data class ResourceDirectoryEntry(
        val nameOrId: Int,
        val offsetToData: Int,
        val isDirectory: Boolean,
        val offset: Int
    )
}
