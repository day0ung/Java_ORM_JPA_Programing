package com.http.connection.proxy.instance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.epopcon.wspider.common.logger.Logger;
import com.epopcon.wspider.common.logger.WSLogger;
import com.epopcon.wspider.common.logger.WSLogger.WSTYPE;
import com.epopcon.wspider.db.dflt.dto.ProxyServerUsageDto;

public class ProxyComparator {

	private static Logger logger = WSLogger.getLogger(WSTYPE.PROXY);
	/*
	static {
		// java.lang.IllegalArgumentException: Comparison method violates its general contract! 에러 처리
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
	}
	 */
	public final static Comparator<ProxyServerUsageDto> WAIT_CNT_COMPARATOR = new Comparator<ProxyServerUsageDto>() {
		@Override
		public int compare(ProxyServerUsageDto o1, ProxyServerUsageDto o2) {
			Integer a = o1.getWaitCnt();
			Integer b = o2.getWaitCnt();
			return compareInternal(a, b, true);
		}
	};

	public final static Comparator<ProxyServerUsageDto> REQ_CNT_COMPARATOR = new Comparator<ProxyServerUsageDto>() {
		@Override
		public int compare(ProxyServerUsageDto o1, ProxyServerUsageDto o2) {
			Long a = o1.getReqCnt();
			Long b = o2.getReqCnt();
			return compareInternal(a, b, true);
		}
	};

	public final static Comparator<ProxyServerUsageDto> REQ_N_USE_CNT_COMPARATOR = new Comparator<ProxyServerUsageDto>() {
		@Override
		public int compare(ProxyServerUsageDto o1, ProxyServerUsageDto o2) {
			Long a = o1.getReqCnt() + o1.getUseCnt();
			Long b = o2.getReqCnt() + o2.getUseCnt();

			return compareInternal(a, b, true);
		}
	};

	public final static Comparator<ProxyServerUsageDto> GROUP_COMPARATOR = new Comparator<ProxyServerUsageDto>() {
		@Override
		public int compare(ProxyServerUsageDto o1, ProxyServerUsageDto o2) {
			Long a = o1.getJobId();
			Long b = o2.getJobId();

			int compare = compareInternal(a, b, true);
			if (compare == 0) {
				a = o1.getProxyServerId();
				b = o2.getProxyServerId();
				compare = compareInternal(a, b, true);
			}
			return compare;
		}
	};

	public final static Comparator<ProxyServerUsageDto> ID_COMPARATOR = new Comparator<ProxyServerUsageDto>() {
		@Override
		public int compare(ProxyServerUsageDto o1, ProxyServerUsageDto o2) {
			Long a = o1.getId();
			Long b = o2.getId();
			return compareInternal(a, b, true);
		}
	};

	public static void sort(List<ProxyServerUsageDto> list, Comparator<ProxyServerUsageDto> comparator) {
		// java.lang.IllegalArgumentException: Comparison method violates its general contract!  
		// 에러 발생 가능성이 있어 3번 시도함
		for (int i = 1; i <= 3; i++) {
			try {
				list.sort(comparator);
				break;
			} catch (Exception e) {
				if (i == 3)
					logger.error(ProxyComparator.class.getSimpleName(), e);
			}
		}
	}

	public static void sortById(List<ProxyServerUsageDto> list, Long id) {
		List<ProxyServerUsageDto> temp = new ArrayList<>(list.size());
		sort(list, ID_COMPARATOR);

		for (Iterator<ProxyServerUsageDto> it = list.iterator(); it.hasNext();) {
			ProxyServerUsageDto e = it.next();
			if (e.getId() < id.longValue()) {
				it.remove();
				temp.add(e);
			}
		}

		for (ProxyServerUsageDto e : temp)
			list.add(e);
	}

	private static int compareInternal(long a, long b, boolean asc) {
		if (a > b)
			return (asc ? 1 : -1);
		else if (a < b)
			return (asc ? -1 : 1);
		else
			return 0;
	}
}
