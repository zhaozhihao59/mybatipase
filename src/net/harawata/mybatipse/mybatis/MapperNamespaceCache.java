/*-******************************************************************************
 * Copyright (c) 2014 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis;

import static net.harawata.mybatipse.MybatipseConstants.*;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.validation.internal.provisional.core.IReporter;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Node;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class MapperNamespaceCache
{
	private static final MapperNamespaceCache INSTANCE = new MapperNamespaceCache();

	private IContentType mapperContentType = Platform.getContentTypeManager()
		.getContentType(CONTENT_TYPE_MAPPER);

	private final Map<String, Map<String, IFile>> cache = new ConcurrentHashMap<String, Map<String, IFile>>();

	public IFile get(IJavaProject javaProject, String namespace, IReporter reporter)
	{
		Map<String, IFile> map = getCacheMap(javaProject, reporter);
		return map.get(namespace);
	}

	public void clear()
	{
		cache.clear();
	}

	public void remove(IProject project)
	{
		cache.remove(project.getName());
	}

	public void remove(String projectName, IFile file)
	{
		Map<String, IFile> map = cache.get(projectName);
		if (map == null)
			return;
		Iterator<Entry<String, IFile>> iterator = map.entrySet().iterator();
		while (iterator.hasNext())
		{
			Entry<String, IFile> entry = iterator.next();
			if (file.equals(entry.getValue()))
			{
				iterator.remove();
			}
		}
	}

	public void put(String projectName, IFile file)
	{
		remove(projectName, file);

		Map<String, IFile> map = cache.get(projectName);
		if (map == null)
			return;

		String namespace = extractNamespace(file);
		if (namespace != null)
		{
			map.put(namespace, file);
		}
	}

	public Map<String, IFile> getCacheMap(IJavaProject javaProject, IReporter reporter)
	{
		String projectName = javaProject.getElementName();
		Map<String, IFile> map = cache.get(projectName);
		if (map == null)
		{
			map = new ConcurrentHashMap<String, IFile>();
			cache.put(projectName, map);
			collectMappers(javaProject, map, reporter);
		}
		return map;
	}

	private void collectMappers(IJavaProject project, final Map<String, IFile> map,
		final IReporter reporter)
	{
		try
		{
			for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots())
			{
				if (root.getKind() != IPackageFragmentRoot.K_SOURCE)
				{
					continue;
				}

				root.getResource().accept(new IResourceProxyVisitor()
				{
					@Override
					public boolean visit(IResourceProxy proxy) throws CoreException
					{
						if (!proxy.isDerived() && proxy.getType() == IResource.FILE
							&& proxy.getName().endsWith(".xml"))
						{
							IFile file = (IFile)proxy.requestResource();
							IContentDescription contentDesc = file.getContentDescription();
							if (contentDesc != null)
							{
								IContentType contentType = contentDesc.getContentType();
								if (contentType != null && contentType.isKindOf(mapperContentType))
								{
									String namespace = extractNamespace(file);
									if (namespace != null)
									{
										map.put(namespace, file);
									}
									return false;
								}
							}
						}
						return true;
					}
				}, IContainer.NONE);
			}
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, "Searching MyBatis Mapper xml failed.", e);
		}
	}

	private String extractNamespace(IFile file)
	{
		IStructuredModel model = null;
		try
		{
			model = StructuredModelManager.getModelManager().getModelForRead(file);
			IDOMModel domModel = (IDOMModel)model;
			IDOMDocument domDoc = domModel.getDocument();

			Node node = XpathUtil.xpathNode(domDoc, "//mapper/@namespace");
			return node == null ? null : node.getNodeValue();
		}
		catch (Exception e)
		{
			Activator.log(Status.ERROR, "Error occurred during parsing mapper:" + file.getFullPath(),
				e);
		}
		finally
		{
			if (model != null)
			{
				model.releaseFromRead();
			}
		}
		return null;
	}

	public static MapperNamespaceCache getInstance()
	{
		return INSTANCE;
	}

	private MapperNamespaceCache()
	{
		super();
	}
}
