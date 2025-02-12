/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package tachyon.worker.block.allocator;

import java.io.IOException;

import com.google.common.base.Preconditions;

import tachyon.worker.block.BlockStoreLocation;
import tachyon.worker.block.BlockMetadataManager;
import tachyon.worker.block.meta.StorageDir;
import tachyon.worker.block.meta.StorageTier;
import tachyon.worker.block.meta.TempBlockMeta;

/**
 * An allocator that allocates a block in the storage dir with most free space. It always allocates
 * to the highest tier if the requested block store location is any tier.
 */
public class MaxFreeAllocator implements Allocator {
  private final BlockMetadataManager mMetaManager;

  public MaxFreeAllocator(BlockMetadataManager metadata) {
    mMetaManager = Preconditions.checkNotNull(metadata);
  }

  @Override
  public TempBlockMeta allocateBlock(long userId, long blockId, long blockSize,
      BlockStoreLocation location) throws IOException {
    StorageDir candidateDir = null;

    if (location.equals(BlockStoreLocation.anyTier())) {
      for (StorageTier tier : mMetaManager.getTiers()) {
        candidateDir = getCandidateDirInTier(tier, blockSize);
        if (candidateDir != null) {
          return new TempBlockMeta(userId, blockId, blockSize, candidateDir);
        }
      }
    } else if (location.equals(BlockStoreLocation.anyDirInTier(location.tierAlias()))) {
      StorageTier tier = mMetaManager.getTier(location.tierAlias());
      candidateDir = getCandidateDirInTier(tier, blockSize);
    } else {
      StorageTier tier = mMetaManager.getTier(location.tierAlias());
      StorageDir dir = tier.getDir(location.dir());
      if (dir.getAvailableBytes() >= blockSize) {
        candidateDir = dir;
      }
    }

    return candidateDir != null ? new TempBlockMeta(userId, blockId, blockSize, candidateDir)
        : null;
  }

  /**
   * Find a directory in a tier that has max free space and is able to store the block.
   *
   * @param tier the storage tier
   * @param blockSize the size of block in bytes
   * @return the storage directory if found, null otherwise
   */
  private StorageDir getCandidateDirInTier(StorageTier tier, long blockSize) {
    StorageDir candidateDir = null;
    long maxFreeBytes = blockSize - 1;
    for (StorageDir dir : tier.getStorageDirs()) {
      if (dir.getAvailableBytes() > maxFreeBytes) {
        maxFreeBytes = dir.getAvailableBytes();
        candidateDir = dir;
      }
    }
    return candidateDir;
  }
}
