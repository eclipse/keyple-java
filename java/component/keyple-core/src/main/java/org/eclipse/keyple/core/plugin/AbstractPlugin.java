/* **************************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.core.plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.core.service.Reader;
import org.eclipse.keyple.core.service.exception.KeypleReaderException;
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException;
import org.eclipse.keyple.core.service.exception.KeypleReaderNotFoundException;

/** Observable plugin. These plugin can report when a reader is added or removed. */
public abstract class AbstractPlugin implements Plugin {

  /** The name of the plugin */
  private final String name;

  /** The list of readers */
  protected Map<String, Reader> readers = new ConcurrentHashMap<String, Reader>();

  /** Registeration status of the plugin */
  private boolean isRegistered;

  /**
   * Instantiates a new Plugin. Retrieve the current readers list.
   *
   * <p>Initialize the list of readers calling the abstract method initNativeReaders
   *
   * <p>When readers initialisation failed, a KeypleReaderException is thrown
   *
   * @param name name of the plugin
   * @throws KeypleReaderException when an issue is raised with reader
   */
  protected AbstractPlugin(String name) {
    this.name = name;
    this.isRegistered = false;
  }

  /** @return the name of the plugin */
  public final String getName() {
    return name;
  }

  /**
   * Returns the current readers name instance map.
   *
   * @return the current readers map, can be an empty
   * @throws IllegalStateException is thrown when plugin is not (or no longer) registered.
   */
  @Override
  public final Map<String, Reader> getReaders() {
    checkStatus();
    return readers;
  }

  /**
   * Returns the current list of reader names.
   *
   * <p>The list of names is built from the current readers list
   *
   * @return a list of String
   * @throws IllegalStateException is thrown when plugin is not (or no longer) registered.
   */
  @Override
  public final Set<String> getReaderNames() {
    checkStatus();
    return readers.keySet();
  }

  /**
   * Init connected native readers (from third party library) and returns a map of corresponding
   * {@link Reader} whith their name as key.
   *
   * <p>{@link Reader} are new instances.
   *
   * <p>this method is called once in the plugin constructor.
   *
   * @return the map of AbstractReader objects.
   * @throws KeypleReaderIOException if the communication with the reader or the card has failed
   */
  protected abstract Map<String, Reader> initNativeReaders();

  /**
   * Gets a specific reader designated by its name in the current readers list
   *
   * @param name of the reader
   * @return the reader
   * @throws KeypleReaderNotFoundException if the wanted reader is not found
   * @throws IllegalStateException is thrown when plugin is not (or no longer) registered.
   */
  @Override
  public final Reader getReader(String name) {
    checkStatus();
    Reader reader = readers.get(name);
    if (reader == null) {
      throw new KeypleReaderNotFoundException(name);
    }
    return reader;
  }

  /**
   * Check if the plugin status is "registered".
   *
   * @throws IllegalStateException is thrown when plugin is not (or no longer) registered.
   */
  protected void checkStatus() {
    if (!isRegistered)
      throw new IllegalStateException(
          String.format("This plugin, %s, is not registered", getName()));
  }

  /** {@inheritDoc} */
  @Override
  public void register() {
    if (isRegistered)
      throw new IllegalStateException(
          String.format("This plugin, %s, is already registered", getName()));
    isRegistered = true;
    readers.putAll(initNativeReaders());
    final Collection<Reader> _readers = readers.values();
    for (Reader seReader : _readers) {
      seReader.register();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void unregister() {
    checkStatus();
    isRegistered = false;
    for (String key : readers.keySet()) {
      final Reader seReader = readers.remove(key);
      seReader.unregister();
    }
  }
}