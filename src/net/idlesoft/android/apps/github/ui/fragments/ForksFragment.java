/*
 * Copyright (c) 2012 Eddie Ringle
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.idlesoft.android.apps.github.ui.fragments;

import android.accounts.AccountsException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import com.viewpagerindicator.TitlePageIndicator;
import net.idlesoft.android.apps.github.R;
import net.idlesoft.android.apps.github.ui.adapters.RepositoryListAdapter;
import net.idlesoft.android.apps.github.ui.widgets.IdleList;
import net.idlesoft.android.apps.github.ui.widgets.ListViewPager;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GsonUtils;
import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.service.RepositoryService;

import java.io.IOException;
import java.util.ArrayList;

import static net.idlesoft.android.apps.github.HubroidConstants.ARG_TARGET_REPO;

public
class ForksFragment extends UIFragment<ForksFragment.ForksDataFragment>
{
	public static final int LIST_FORKS = 1;

	protected
	class ListHolder
	{
		ArrayList<Repository> repositories;
		CharSequence title;

		int type;
		PageIterator<Repository> request;
	}

	public static
	class ForksDataFragment extends DataFragment
	{
		ArrayList<ListHolder> repositoryLists;
		Repository targetRepository;
		int currentItem;
		int currentItemScroll;

		public
		int findListIndexByType(int listType)
		{
			if (repositoryLists == null) return -1;

			for (ListHolder holder : repositoryLists) {
				if (holder.type == listType)
					return repositoryLists.indexOf(holder);
			}

			return -1;
		}
	}

	ListViewPager mViewPager;
	TitlePageIndicator mTitlePageIndicator;
	int mCurrentPage;

	public
	ForksFragment()
	{
		super(ForksDataFragment.class);
	}

	@Override
	public
	View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		if (container == null)
			getBaseActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();

		View v = inflater.inflate(R.layout.viewpager_fragment, container, false);

		if (v != null) {
			mViewPager = (ListViewPager) v.findViewById(R.id.vp_pages);
			mTitlePageIndicator = (TitlePageIndicator) v.findViewById(R.id.tpi_header);
		}

		return v;
	}

	@Override
	public
	void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		final Bundle args = getArguments();
		final String repositoryJson;
		if (args != null) {
			repositoryJson = args.getString(ARG_TARGET_REPO, null);
			if (repositoryJson != null) {
				mDataFragment.targetRepository = GsonUtils.fromJson(repositoryJson,
																	Repository.class);
			}
		}
		getBaseActivity().getSupportActionBar().setTitle(mDataFragment.targetRepository.getName());
		if (mDataFragment.repositoryLists == null)
			mDataFragment.repositoryLists = new ArrayList<ListHolder>();
		ListViewPager.MultiListPagerAdapter adapter =
				new ListViewPager.MultiListPagerAdapter(getContext());

		mViewPager.setAdapter(adapter);
		mTitlePageIndicator.setViewPager(mViewPager);

		fetchData(false);

		mViewPager.setCurrentItem(mDataFragment.currentItem);
	}

	public
	void fetchData(boolean freshen)
	{
		/* Display a user's repositories */
		final IdleList<Repository> list = new IdleList<Repository>(getContext());
		final ListHolder holder;

		list.setAdapter(new RepositoryListAdapter(getBaseActivity()));

		final int index = mDataFragment.findListIndexByType(LIST_FORKS);

		if (index >= 0) {
			holder = mDataFragment.repositoryLists.get(index);

			list.setTitle(holder.title);
			list.getListAdapter().fillWithItems(holder.repositories);
			list.getListAdapter().notifyDataSetChanged();
		} else {
			holder = new ListHolder();
			holder.type = LIST_FORKS;
			holder.title = getString(R.string.forks);
			list.setTitle(holder.title);
			holder.repositories = new ArrayList<Repository>();

			mDataFragment.repositoryLists.add(holder);

			final DataFragment.DataTask.DataTaskRunnable forksRunnable =
					new DataFragment.DataTask.DataTaskRunnable()
					{
						@Override
						public
						void runTask() throws InterruptedException
						{
							try {
								final RepositoryService rs =
										new RepositoryService(getBaseActivity().getGHClient());
								holder.request = rs.pageForks(new RepositoryId(
										mDataFragment.targetRepository.getOwner().getLogin(),
										mDataFragment.targetRepository.getName()));
								holder.repositories.addAll(holder.request.next());
							} catch (IOException e) {
								e.printStackTrace();
							} catch (AccountsException e) {
								e.printStackTrace();
							}
						}
					};

			final DataFragment.DataTask.DataTaskCallbacks forksCallbacks =
					new DataFragment.DataTask.DataTaskCallbacks()
					{
						@Override
						public
						void onTaskStart()
						{
							list.getProgressBar().setVisibility(View.VISIBLE);
							list.setFooterShown(true);
							list.setListShown(true);
						}

						@Override
						public
						void onTaskCancelled()
						{
						}

						@Override
						public
						void onTaskComplete()
						{
							list.setListShown(false);
							list.getProgressBar().setVisibility(View.GONE);
							list.getListAdapter().fillWithItems(holder.repositories);
							list.getListAdapter().notifyDataSetChanged();
							list.setListShown(true);
						}
					};

			mDataFragment.executeNewTask(forksRunnable, forksCallbacks);
		}

		list.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public
			void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				final Repository target = holder.repositories.get(position);
				final Bundle args = new Bundle();
				args.putString(ARG_TARGET_REPO, GsonUtils.toJson(target));
				getBaseActivity().startFragmentTransaction();
				getBaseActivity().addFragmentToTransaction(RepositoryFragment.class,
														   R.id.fragment_container_more,
														   args);
				getBaseActivity().finishFragmentTransaction();
			}
		});

		mViewPager.getAdapter().addList(list);
	}

	@Override
	public
	void onDestroy()
	{
		super.onDestroy();

		mDataFragment.currentItem = mViewPager.getCurrentItem();
	}
}
