#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <pthread.h>
#include <stdatomic.h>
#include <assert.h>
#include <dat3m.h>

typedef struct {
    int value;
    atomic_int next;
} SafeStackItem;

atomic_int head;
atomic_int count;
SafeStackItem array[3];

void init()
{
    // Creates stack of fixed size 3
    atomic_init(&count, 3);
    atomic_init(&head, 0);

    atomic_init(&array[0].next, 1);
    atomic_init(&array[1].next, 2);
    atomic_init(&array[2].next, -1);
}

int pop(intptr_t idx)
{
    int i = 0;
    while (atomic_load_explicit(&count, memory_order_acquire) > 1) {
        int head1 = atomic_load_explicit(&head, memory_order_acquire);
        if ((idx == 1 || idx == 2) && i == 0)
            __VERIFIER_schedule(2 * idx - 1);
        int next1 = atomic_exchange_explicit(&array[head1].next, -1, memory_order_seq_cst);

        if (next1 >= 0) {
            int head2 = head1;
            if (atomic_compare_exchange_strong_explicit(&head, &head2, next1, memory_order_seq_cst, memory_order_seq_cst)) {
                atomic_fetch_sub_explicit(&count, 1, memory_order_seq_cst);
                return head1;
            } else {
                // Why Xchg instead of a simple store?
                atomic_exchange_explicit(&array[head1].next, next1, memory_order_seq_cst);
                if (idx == 1 && i == 0)
                    __VERIFIER_schedule(2);
            }
        }
        i++;
    }
    return -1;
}

void push(intptr_t idx, int index)
{
    int head1 = atomic_load_explicit(&head, memory_order_acquire);
    int i = 0;
    do {
        atomic_store_explicit(&array[index].next, head1, memory_order_release);
        if (idx == 0 && i == 0)
            __VERIFIER_schedule(0);
        i++;
    } while (atomic_compare_exchange_strong_explicit(&head, &head1, index, memory_order_seq_cst, memory_order_seq_cst) == 0);
    atomic_fetch_add_explicit(&count, 1, memory_order_seq_cst);
}

void *thread3(void *arg)
{
    intptr_t idx = ((intptr_t) arg);

    int elem;
    // Unrolling of the original C++ Code
    for (int i = 0; ; i++) {
        elem = pop(idx == 1 && i == 0 ? idx : -1);
        if (elem >= 0) {
            break;
        }

    }
    array[elem].value = idx;
    assert (array[elem].value == idx);
    push(idx, elem);

    while (1) {
        elem = pop(-1);
        if (elem >= 0)
            break;
    }
    array[elem].value = idx;
    assert (array[elem].value == idx);
    return NULL;
}

void *thread1(void *arg)
{
    intptr_t idx = ((intptr_t) arg);

    int elem;
    // Unrolling of the original C++ Code
    for (int i = 0; ; i++) {
        elem = pop(idx == 2 && i == 0 ? idx : -1);
        if (elem >= 0) {
            break;
        }
    }
    return NULL;
}

int main()
{
    pthread_t t0, t1, t2;

    init();
    
    pthread_create(&t0, NULL, thread3, (void *) 0);
    pthread_create(&t1, NULL, thread3, (void *) 1);
    pthread_create(&t2, NULL, thread1, (void *) 2);

    return 0;
}
