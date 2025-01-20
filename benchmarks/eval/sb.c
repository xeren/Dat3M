#include <stdlib.h>
#include <pthread.h>
#include <stdatomic.h>
#include <assert.h>

#define N 7

atomic_int x[N];
atomic_int y[N];

int r[N];

void *thread_n(void *thread_id_ptr)
{
	int thread_id = (int) thread_id_ptr;
	atomic_store_explicit(&x[thread_id], 1, memory_order_relaxed);
	r[thread_id] = atomic_load_explicit(&x[N - thread_id - 1], memory_order_relaxed);
	return NULL;
}

int main()
{
	pthread_t t[N];

	for (int i = 0; i < N; ++i) {
		pthread_create(&t[i], NULL, thread_n, (void*)i);
	}

	for (int i = 0; i < N; ++i) {
		pthread_join(t[i], NULL);
	}

	int value_read = 0;
	for (int i = 0; i < N; ++i) {
		if (r[i] == 1) {
			value_read = 1;
		}
 	}
	assert(value_read);
	return 0;
}
