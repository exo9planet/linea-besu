/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.services.kvstore;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage.Transaction;

import java.util.function.Supplier;

/**
 * The Segmented key value storage transaction transition validator decorator.
 *
 * @param <S> the type parameter
 */
public class SegmentedKeyValueStorageTransactionTransitionValidatorDecorator<S>
    implements Transaction<S> {

  private final Transaction<S> transaction;
  private final Supplier<Boolean> isClosed;
  private boolean active = true;

  /**
   * Instantiates a new Segmented key value storage transaction transition validator decorator.
   *
   * @param toDecorate the to decorate
   * @param isClosed supplier that returns true if the storage is closed
   */
  public SegmentedKeyValueStorageTransactionTransitionValidatorDecorator(
      final Transaction<S> toDecorate, final Supplier<Boolean> isClosed) {
    this.transaction = toDecorate;
    this.isClosed = isClosed;
  }

  @Override
  public final void put(final S segment, final byte[] key, final byte[] value) {
    checkState(active, "Cannot invoke put() on a completed transaction.");
    checkState(!isClosed.get(), "Cannot invoke put() on a closed storage.");
    transaction.put(segment, key, value);
  }

  @Override
  public final void remove(final S segment, final byte[] key) {
    checkState(active, "Cannot invoke remove() on a completed transaction.");
    checkState(!isClosed.get(), "Cannot invoke remove() on a closed storage.");
    transaction.remove(segment, key);
  }

  @Override
  public final void commit() throws StorageException {
    checkState(active, "Cannot commit a completed transaction.");
    checkState(!isClosed.get(), "Cannot invoke commit() on a closed storage.");
    active = false;
    transaction.commit();
  }

  @Override
  public final void rollback() {
    checkState(active, "Cannot rollback a completed transaction.");
    checkState(!isClosed.get(), "Cannot invoke rollback() on a closed storage.");
    active = false;
    transaction.rollback();
  }
}
