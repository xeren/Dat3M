#include <stdlib.h>
#include <pthread.h>
#include <stdatomic.h>
#include <assert.h>

#define N 8

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

	pthread_create(&t[0], NULL, thread_n, (void*)0);
	pthread_create(&t[1], NULL, thread_n, (void*)1);
	pthread_create(&t[2], NULL, thread_n, (void*)2);
	pthread_create(&t[3], NULL, thread_n, (void*)3);
	pthread_create(&t[4], NULL, thread_n, (void*)4);
	pthread_create(&t[5], NULL, thread_n, (void*)5);
	pthread_create(&t[6], NULL, thread_n, (void*)6);
	pthread_create(&t[7], NULL, thread_n, (void*)7);

	pthread_join(t[0], NULL);
	pthread_join(t[1], NULL);
	pthread_join(t[2], NULL);
	pthread_join(t[3], NULL);
	pthread_join(t[4], NULL);
	pthread_join(t[5], NULL);
	pthread_join(t[6], NULL);
	pthread_join(t[7], NULL);

	int value_read = 0;
	if (r[0] == 1) {
		value_read = 1;
	}
	if (r[1] == 1) {
		value_read = 1;
	}
	if (r[2] == 1) {
		value_read = 1;
	}
	if (r[3] == 1) {
		value_read = 1;
	}
	if (r[4] == 1) {
		value_read = 1;
	}
	if (r[5] == 1) {
		value_read = 1;
	}
	if (r[6] == 1) {
		value_read = 1;
	}
	if (r[7] == 1) {
		value_read = 1;
	}

	assert(value_read);
	return 0;
}
